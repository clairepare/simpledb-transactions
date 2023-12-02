package simpledb;

import java.util.concurrent.*;
import java.util.*
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

    public LockManager() {
        exLocks = new ConcurrentHashMap<PageId, TransactionId>();
        shLocks = new ConcurrentHashMap<PageId, ArrayList<TransactionId>>();
    }

    public static void addSharedLock(PageId pid, TransactionId tid) {
        while (exLocks.containsKey(pid)) {
            Thread.sleep(.01);
        }
        if (shLocks.containsKey(pid)) {
            shLocks.get(pid).add(tid);
        }
        else {
            ArrayList<TransactionId> temp = new ArrayList<TransactionId>();
            temp.add(tid);
            shLocks.put(pid, temp);
        }
    }

    public void write(PageId pid, TransactionId tid) {
        boolean has = false;
        for (TransactionId t : shLocks.get(pid)) {
            if (t.equals(tid)) {
                has = true;
            }
        }
        if (has) {
            upgradeLock(pid, tid);
        }
        else {
            addExclusiveLock(pid, tid);
        }
    }
    private void addExclusiveLock(PageId pid, TransactionId tid) {
        while (exLocks.containsKey(pid)) {
            Thread.sleep(.01);
        }
        exLocks.put(pid, tid);
    }

    private void upgradeLock(PageId pid, TransactionId tid) {
        while (exLocks.containsKey(pid) || shLocks.get(pid).size() > 1) {
            Thread.sleep(.01);
        }
        shLocks.remove(pid);
        exLocks.put(pid, tid);
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