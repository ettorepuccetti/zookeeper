package master.eit.manager;

import java.util.concurrent.CountDownLatch;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.Watcher.Event.EventType;



public class RegisterWatcher implements Watcher, Runnable {

    ZooManager zm;
    CountDownLatch onetime = new CountDownLatch(1);


    public RegisterWatcher(ZooManager zoo) {
        this.zm = zoo;
        System.out.println("Watcher set");

    }

    public void process(WatchedEvent we) {
        if (we.getType() == EventType.NodeChildrenChanged) {
            System.out.println("Watcher triggered !!");
            try {
                zm.register();
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
            System.out.println("Watcher thread ends");
        }
    }
}