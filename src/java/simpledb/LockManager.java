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
    private final static int TIMEOUT = 1000;

    public LockManager() {
        exLocks = new ConcurrentHashMap<PageId, TransactionId>();
        shLocks = new ConcurrentHashMap<PageId, ArrayList<TransactionId>>();
    }

    public static void addSharedLock(PageId pid, TransactionId tid) {
        System.out.println("Transaction " + tid + " is trying to acquired a shared lock on page " + pid);
        int time = 0;
        while (exLocks.containsKey(pid) && time < TIMEOUT) {
            System.out.println("waiting");
            try {
                Thread.sleep(10);
                time += 10;
            }
            catch (InterruptedException e) {
                System.out.println("sleep interrupted by " + e);
            }
        }
        if (time >= TIMEOUT) System.out.println("timed out waiting");
        if (shLocks.containsKey(pid)) {
            shLocks.get(pid).add(tid);
            System.out.println("Transactions with a shared lock on page " + pid + ":\n" + shLocks.get(pid));
        }
        else {
            ArrayList<TransactionId> temp = new ArrayList<TransactionId>();
            temp.add(tid);
            shLocks.put(pid, temp);
            System.out.println("Transactions with a shared lock on page " + pid + ":\n" + shLocks.get(pid));
        }
    }

    public void write(PageId pid, TransactionId tid) {
        boolean has = false;
        if (shLocks.containsKey(pid)) {
            for (TransactionId t : shLocks.get(pid)) {
                if (t.equals(tid)) {
                    has = true;
                }
            }
        }
        if (has) {
            System.out.println("tid " + tid + " holds the lock for page " + pid);
            upgradeLock(pid, tid);
        }
        else {
            System.out.println("tid " + tid + " does not hold the lock for page " + pid);
            addExclusiveLock(pid, tid);
        }
    }
    private void addExclusiveLock(PageId pid, TransactionId tid) {
        int time = 0;
        while (exLocks.containsKey(pid) && time < TIMEOUT) {
            try {
                Thread.sleep(10);
                time += 10;
            }
            catch (InterruptedException e) {
                System.out.println("sleep interrupted by " + e);
            }
        }
        if (time >= TIMEOUT) System.out.println("timed out waiting");
        exLocks.put(pid, tid);
        System.out.println("Transaction " + tid + " has acquired an exclusive lock on page " + pid);
    }

    private void upgradeLock(PageId pid, TransactionId tid) {
        int time = 0;
        while ((exLocks.containsKey(pid) || shLocks.get(pid).size() > 1) && time < TIMEOUT) {
            try {
                Thread.sleep(10);
                time += 10;
            }
            catch (InterruptedException e) {
                System.out.println("sleep interrupted by " + e);
            }
        }
        if (time >= TIMEOUT) System.out.println("timed out waiting");
        shLocks.remove(pid);
        exLocks.put(pid, tid);
        System.out.println("Transaction " + tid + " has upgraded to an exclusive lock on page " + pid);
    }

    public void release(TransactionId tid, PageId pid) {
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
        System.out.println("Transaction " + tid + " has released its lock on page " + pid);
    }

    public void release(TransactionId tid) {
        exLocks.values().remove(tid);
        Iterator<ArrayList<TransactionId>> it = shLocks.values().iterator();
        ArrayList<TransactionId> curr = null;
        while (it.hasNext()) {
            curr = it.next();
            curr.remove(tid);
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