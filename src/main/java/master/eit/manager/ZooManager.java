package master.eit.manager;

import java.io.IOException;
import java.util.List;
import java.util.Scanner;
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

public class ZooManager implements Runnable {
    
    CountDownLatch timeout = new CountDownLatch(1);
    ZooKeeper zoo;

    public ZooManager () throws KeeperException, InterruptedException {
        
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

        setUp();
        
    }

    public void setUp() throws KeeperException, InterruptedException {

        zoo.create(("/request"), null, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        zoo.create(("/request/enroll"), null, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        zoo.create(("/request/quit"), null, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        zoo.create(("/registry"), null, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        zoo.create(("/online"), null, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
    }


    public void register () throws KeeperException, InterruptedException {
        Stat stat_exist = zoo.exists("/request/enroll", true) ;
        if (stat_exist != null) {
            try {
                List<String> children;
                RegisterManagerWatcher rw = new RegisterManagerWatcher(this);
                Thread rwThread = new Thread(rw);
                rwThread.setName("register watcher");
                rwThread.start();
                children = zoo.getChildren("/request/enroll", rw);
                for (int i = 0; i < children.size(); i++) {
                    String child = children.get(i);
                    byte[] bdata = zoo.getData("/request/enroll/" + child, true, null);
                    String data = new String(bdata, "UTF-8");
                    if (data.equals(new String("-1"))) {
                        int version_request = zoo.exists("/request/enroll", true).getVersion();
                        try {
                            Stat stat_registry = zoo.exists("/registry/" + child, true);
                            if (stat_registry == null) {
                                zoo.create("/registry/" + child, null, ZooDefs.Ids.OPEN_ACL_UNSAFE,CreateMode.PERSISTENT);
                                int version_registry = zoo.exists("/registry/" + child, true).getVersion();
                                zoo.setData("/registry/" + child, "0".getBytes(), version_registry);     //0 means it's going to be the first time online
                                zoo.setData("/request/enroll/" + child, "1".getBytes(), version_request);
                            }
                            else {
                                zoo.setData("/request/enroll/" + child, "2".getBytes(), version_request);                                
                            }
                        } catch (KeeperException e){
                            zoo.setData("/request/enroll/" + child, "0".getBytes(), version_request);
                            e.printStackTrace();
                        }
                    }
                }
            } catch (Exception e ) {
                e.printStackTrace();
            }   
        }
    }

    public void quit () throws KeeperException, InterruptedException {
        Stat stat_exist = zoo.exists("/request/quit", true) ;
        if (stat_exist != null) {
            try {
                List<String> children;
                QuitManagerWatcher qw = new QuitManagerWatcher(this);
                Thread qwThread = new Thread(qw);
                qwThread.setName("quit watcher");
                qwThread.start();
                children = zoo.getChildren("/request/quit", qw);
                for (int i = 0; i < children.size(); i++) {
                    String child = children.get(i);
                    byte[] bdata = zoo.getData("/request/quit/" + child, true, null);
                    String data = new String(bdata, "UTF-8");
                    if (data.equals("-1")) {
                        int version_request = zoo.exists("/request/quit", true).getVersion();
                        try {
                            Stat stat_registry = zoo.exists("/registry/" + child, true);
                            if (stat_registry != null) {
                                zoo.delete("/registry/" + child, stat_registry.getVersion());
                                zoo.setData("/request/quit/" + child, "1".getBytes(), version_request);
                            }
                            else {
                                zoo.setData("/request/quit/" + child, "2".getBytes(), version_request);                                
                            }
                        } catch (KeeperException e){
                            zoo.setData("/request/quit/" + child, "0".getBytes(), version_request);
                            e.printStackTrace();
                        }
                    }
                }
            } catch (Exception e ) {
                e.printStackTrace();
            }   
        }
    }
    public void goOnline() throws KeeperException, InterruptedException {
        Stat stat_exist = zoo.exists("/online", true);
        if (stat_exist != null) {
            try {
                List<String> children;
                OnlineManagerWatcher onlineManagerWatcher = new OnlineManagerWatcher(this);
                Thread onlineManagerThread = new Thread(onlineManagerWatcher);
                onlineManagerThread.setName("online watcher");
                onlineManagerThread.start();
                children = zoo.getChildren("/online", onlineManagerWatcher);
                for (int i = 0; i < children.size(); i++) {
                    String child = children.get(i);
                    byte[] byteData = zoo.getData("/online/" + child, true,null);
                    String data = new String(byteData, "UTF-8");
                    if (data.equals("-1")) {
                        int version_request = zoo.exists("/online", true).getVersion();
                        int version_registry = zoo.exists("/registry/" + child, true).getVersion();
                        System.out.println("Will change node data");
                        try {
                            Stat stat_registry = zoo.exists("/registry/" + child, true);
                            String firstTimeOnline = new String(zoo.getData("/registry/" + child, true,  null), "UTF-8");
                            System.out.println(firstTimeOnline + "condition: ");
                            if (stat_registry != null && firstTimeOnline.equals("0")) {
                                zoo.setData("/registry/" + child, "1".getBytes(), version_registry);    //1 means the node was online already at least once
                                //Create /topic/w_id in Kafka
                            }
                        } catch (KeeperException e) {
                            e.printStackTrace();
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void run() {
        try {
            register();
            goOnline();
            quit();
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