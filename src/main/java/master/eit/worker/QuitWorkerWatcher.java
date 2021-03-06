package master.eit.worker;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;

import java.util.concurrent.CountDownLatch;

public class QuitWorkerWatcher implements Watcher, Runnable {
    ZooWorker zw;
    public CountDownLatch onetime = new CountDownLatch(1);


    public QuitWorkerWatcher(ZooWorker zoo) {
        this.zw = zoo;

    }

    public void process(WatchedEvent we) {
        if (we.getType() == Watcher.Event.EventType.NodeDataChanged) {
            try {
                zw.removeQuit();
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
        }
    }
}
