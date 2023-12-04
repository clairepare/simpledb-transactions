package simpledb;

import java.util.concurrent.*;
import java.util.*;
import java.util.ArrayList;
import java.lang.Thread;
import simpledb.common.*;
import simpledb.execution.*;
import simpledb.optimizer.*;
import simpledb.storage.*;
import simpledb.transaction.*;

public class LockManager {
    private static ConcurrentHashMap<PageId, TransactionId> exLocks;
    private static ConcurrentHashMap<PageId, ArrayList<TransactionId>> shLocks;
    private final static int TIMEOUT = 100;
    private static Object lock1;

    //graph implementation and methods obtained from https://www.baeldung.com/java-graphs

    private static class Vertex {
        TransactionId t;
        Vertex(TransactionId tid) {
            t = tid;
        }
    }

    private static ConcurrentHashMap<Vertex, ArrayList<Vertex>> dependencyGraph;

    public LockManager() {
        exLocks = new ConcurrentHashMap<PageId, TransactionId>();
        shLocks = new ConcurrentHashMap<PageId, ArrayList<TransactionId>>();
        lock1 = new Object();
        dependencyGraph = new ConcurrentHashMap<Vertex, ArrayList<Vertex>>();
    }

    private static void addVertex(TransactionId tid) {
        System.out.println("adding vertex " + tid);
        dependencyGraph.putIfAbsent(new Vertex(tid), new ArrayList<>());
    }

    private static void removeVertex(TransactionId tid) {
        System.out.println("removing vertex" + tid);
        Vertex v = new Vertex(tid);
        dependencyGraph.values().stream().forEach(e -> e.remove(v));
        dependencyGraph.remove(new Vertex(tid));
    }

    private static void addEdge(TransactionId t1, TransactionId t2) {
        Vertex v1 = new Vertex(t1);
        Vertex v2 = new Vertex(t2);
        System.out.println("adding edge for " + v1 + " and " + v2);
        dependencyGraph.get(v1).add(v2);
    }

    private static void removeEdge(TransactionId t1, TransactionId t2) {
        Vertex v1 = new Vertex(t1);
        Vertex v2 = new Vertex(t2);
        List<Vertex> eV1 = dependencyGraph.get(v1);
        if (eV1 != null)
            eV1.remove(v2);
        System.out.println("removing edge for " + v1 + " and " + v2);
    }

    private static boolean cycle() {
        for (Vertex v : dependencyGraph.keySet()) {
            if (dfs(v)) {
                System.out.println("cycle detected!");
                return true;
            }
        }
        return false;
    }
    private static boolean dfs(Vertex v) {
        ConcurrentHashMap<Vertex, Boolean> visited = new ConcurrentHashMap<Vertex, Boolean>();
        if (!visited.containsKey(v)) {
            visited.put(v, true);
            ArrayList<Vertex> neighbors = dependencyGraph.get(v);
            boolean ret = false;
            for (Vertex n : neighbors) {
                ret = ret || dfs(n);
            }
            return ret;
        }
        else{
            return true;
        }
    }

    public static void addSharedLock(PageId pid, TransactionId tid) throws TransactionAbortedException {
        //System.out.println("Transaction " + tid + " is trying to acquired a shared lock on page " + pid);
        //int time = 0;
        if (exLocks.containsKey(pid) && exLocks.get(pid).equals(tid)) {
            //we have a BETTER lock
            return;
        }
        if (shLocks.containsKey(pid)) {
            for (TransactionId t: shLocks.get(pid)) {
                if (t.equals(tid)) {
                    //we have the lock already
                    return;
                }
            }
        }
        //add to graph
        addVertex(tid);
        if (exLocks.containsKey(pid)) {
            TransactionId t = exLocks.get(pid);
            addVertex(t);
            addEdge(tid, t);
            while (exLocks.containsKey(pid) /*&& time < TIMEOUT*/) {
                //System.out.println("waiting");
                if (cycle()) {
                    System.out.println("deadlock detected for " + tid);
                    throw new TransactionAbortedException();
                }
                try {
                    Thread.sleep(10);
                    //time += 10;
                }
                catch (InterruptedException e) {
                    System.out.println("sleep interrupted by " + e);
                }
            }
            removeEdge(tid, t);
        }

        //if (time >= TIMEOUT) throw new Exception("Timed out waiting for shared lock");
        synchronized (lock1) {
            if (shLocks.containsKey(pid)) {
                shLocks.get(pid).add(tid);
                //System.out.println("Transactions with a shared lock on page " + pid + ":\n" + shLocks.get(pid));
            }
            else {
                ArrayList<TransactionId> temp = new ArrayList<TransactionId>();
                temp.add(tid);
                shLocks.put(pid, temp);
                //System.out.println("Transactions with a shared lock on page " + pid + ":\n" + shLocks.get(pid));
            }
        }
    }

    public void write(PageId pid, TransactionId tid) {
        boolean has = false;
        if (exLocks.containsKey(pid) && exLocks.get(pid).equals(tid)) {
            //we have the lock already
            return;
        }
        if (shLocks.containsKey(pid)) {
            for (TransactionId t : shLocks.get(pid)) {
                if (t.equals(tid)) {
                    has = true;
                }
            }
        }
        try {
            if (has) {
                //System.out.println("tid " + tid + " holds the lock for page " + pid);
                upgradeLock(pid, tid);
            } else {
                //System.out.println("tid " + tid + " does not hold the lock for page " + pid);
                addExclusiveLock(pid, tid);
            }
        }
        catch (Exception e) {
            System.out.println(e);
        }
    }
    private void addExclusiveLock(PageId pid, TransactionId tid) throws TransactionAbortedException {
        //int time = 0;
        addVertex(tid);
        if ((exLocks.containsKey(pid) || shLocks.containsKey(pid))) {
            TransactionId t = exLocks.get(pid);
            addVertex(t);
            addEdge(tid, t);
            while ((exLocks.containsKey(pid) || shLocks.containsKey(pid)) /*&& time < TIMEOUT*/) {
                if (cycle()) {
                    System.out.println("deadlock detected for " + tid);
                    throw new TransactionAbortedException();
                }
                try {
                    Thread.sleep(10);
                    //time += 10;
                } catch (InterruptedException e) {
                    System.out.println("sleep interrupted by " + e);
                }
            }
            removeEdge(tid, t);
        }        //if (time >= TIMEOUT) throw new Exception("Timed out waiting for exclusive lock");

        synchronized (lock1) {
            exLocks.put(pid, tid);
            //System.out.println("Transaction " + tid + " has acquired an exclusive lock on page " + pid);
        }
    }

    private void upgradeLock(PageId pid, TransactionId tid) throws TransactionAbortedException {
        //int time = 0;
        addVertex(tid);
        if (exLocks.containsKey(pid)|| shLocks.get(pid).size() > 1) {
            TransactionId t = exLocks.get(pid);
            addVertex(t);
            addEdge(tid, t);
            while ((exLocks.containsKey(pid) || shLocks.get(pid).size() > 1) /*&& time < TIMEOUT*/) {
                if (cycle()) {
                    System.out.println("deadlock detected for " + tid);
                    throw new TransactionAbortedException();
                }
                try {
                    Thread.sleep(10);
                    //time += 10;
                } catch (InterruptedException e) {
                    System.out.println("sleep interrupted by " + e);
                }
            }
            removeEdge(tid,t);
        }
        //if (time >= TIMEOUT) throw new Exception("Timed out waiting to upgrade lock");
        synchronized (lock1) {
            shLocks.remove(pid);
            exLocks.put(pid, tid);
            //System.out.println("Transaction " + tid + " has upgraded to an exclusive lock on page " + pid);
        }
    }

    public void release(TransactionId tid, PageId pid) {
        synchronized (lock1) {
            if (shLocks.containsKey(pid)) {
                ArrayList<TransactionId> temp = shLocks.get(pid);
                if (temp.size() > 1) {
                    temp.remove(tid);
                }
                shLocks.remove(pid);
            }
            if (exLocks.containsKey(pid)) {
                exLocks.remove(pid);
            }
            //System.out.println("Transaction " + tid + " has released its lock on page " + pid);
        }
    }

    public void release(TransactionId tid) {
        synchronized(lock1) {
            exLocks.values().remove(tid);
            Iterator<ArrayList<TransactionId>> it = shLocks.values().iterator();
            ArrayList<TransactionId> curr = null;
            while (it.hasNext()) {
                curr = it.next();
                curr.remove(tid);
            }
        }

    }

    public void release(PageId pid) {
        synchronized(lock1) {
            exLocks.remove(pid);
            shLocks.remove(pid);
        }

    }

    public String getLockType(TransactionId tid, PageId pid) {
        if (exLocks.containsKey(pid)) {
            return "Exclusive";
        }
        ArrayList<TransactionId> temp = shLocks.get(pid);
        for (TransactionId t: temp) {
            if (t.equals(tid)) {
                return "Shared";
            }
        }
        return null;
    }
}