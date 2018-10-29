package master.eit.worker;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.Watcher.Event.KeeperState;
import org.apache.zookeeper.ZooKeeper.States;
import org.apache.zookeeper.data.Stat;

public class ZooWorker implements Runnable{
    
    CountDownLatch timeout = new CountDownLatch(1);
    ZooKeeper zoo;
    String name;

    public ZooWorker (String name) throws KeeperException, InterruptedException {
        
        this.name = name;
        
        try {
            zoo = new ZooKeeper("localhost", 1000, new Watcher() {
                public void process(WatchedEvent we) {
                    if (we.getState() == KeeperState.SyncConnected) {
                        timeout.countDown();
                    }
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }

        timeout.await(100, TimeUnit.MILLISECONDS);
        States state = zoo.getState();
        System.out.println(state);
    }

    public void register() throws KeeperException, InterruptedException {
        zoo.create("/request/enroll/" + name , "-1".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
    }

    public void watchCreatedNodes () throws KeeperException, InterruptedException {
        RegisterWorkerWatcher rww = new RegisterWorkerWatcher(this);
        Thread rwThread = new Thread(rww);
        rwThread.setName("register worker watcher");
        rwThread.start();
        byte[] data = zoo.getData("/request/enroll/" + name, rww, null);
        if (data.toString() == "1"| data.toString() == "2") {
            // if I'm here it means the action that I'm watching has already happen,
            // so I do what the watcher is supposed to do, remove the node and release the watcher thread
            remove();
            rww.onetime.countDown();
        }

    }

    public void remove () throws KeeperException, InterruptedException {
        int version_delete = zoo.exists("/request/enroll" + name, true).getVersion();
        zoo.delete("/request/enroll" + name, version_delete);
    }


    public void run() {
        try {
            register();
            watchCreatedNodes();
            synchronized (this) {
                while(true) {
                    wait();
                }
            }
        } catch (KeeperException e1) {
            e1.printStackTrace();
        } catch (InterruptedException e1) {
            e1.printStackTrace();
            Thread.currentThread().interrupt();
        }
    }
}