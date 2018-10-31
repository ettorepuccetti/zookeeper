package master.eit.worker;

import java.util.concurrent.CountDownLatch;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.Watcher.Event.EventType;



public class RegisterWorkerWatcher implements Watcher, Runnable {

    ZooWorker zw;
    public CountDownLatch onetime = new CountDownLatch(1);


    public RegisterWorkerWatcher(ZooWorker zoo) {
        this.zw = zoo;
        System.out.println("Register Worker Watcher set");

    }

    public void process(WatchedEvent we) {
        if (we.getType() == EventType.NodeDataChanged) {
            System.out.println("Register Worker Watcher triggered !!");
            try {
                zw.removeRequest();
            } catch (KeeperException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        onetime.countDown();
    }

    public void run() {
        synchronized(this) {
            try {
                onetime.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            System.out.println("Register Watcher thread ends");
        }
    }
}