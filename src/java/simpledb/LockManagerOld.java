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

public class LockManagerOld{
    private static ConcurrentHashMap<PageId, TransactionId> exLocks;
    private static ConcurrentHashMap<PageId, ArrayList<TransactionId>> shLocks;
    private final static int TIMEOUT = 1000;

    // Exception to indicate timeout
    public static class LockTimeoutException extends Exception {
        public LockTimeoutException(String message) {
            super(message);
        }
    }

    public LockManagerOld() {
        exLocks = new ConcurrentHashMap<PageId, TransactionId>();
        shLocks = new ConcurrentHashMap<PageId, ArrayList<TransactionId>>();
    }

    public static void addSharedLock(PageId pid, TransactionId tid) throws LockTimeoutException, InterruptedException {
        System.out.println("addSharedLock: Transaction " + tid + " is trying to acquire a shared lock on page " + pid);
        int time = 0;
        while (exLocks.containsKey(pid) && time < TIMEOUT) {
            System.out.println("addSharedLock: Waiting for exclusive lock to be released on page " + pid);
            try {
                Thread.sleep(10);
                time += 10;
            } catch (InterruptedException e) {
                System.out.println("addSharedLock: Sleep interrupted for Transaction " + tid + " on page " + pid + " - " + e);
                throw new InterruptedException("Thread interrupted while waiting for shared lock");
            }
        }
        if (time >= TIMEOUT) {
            System.out.println("addSharedLock: Timeout while waiting for shared lock by Transaction " + tid + " on page " + pid);
            throw new LockTimeoutException("Timeout while waiting for shared lock");
        }
        shLocks.computeIfAbsent(pid, k -> new ArrayList<>()).add(tid);
        System.out.println("addSharedLock: Transaction " + tid + " acquired a shared lock on page " + pid);
    }

    public void write(PageId pid, TransactionId tid) throws LockTimeoutException, InterruptedException {
        System.out.println("write: Transaction " + tid + " is trying to write to page " + pid);
        boolean has = false;
        if (shLocks.containsKey(pid)) {
            for (TransactionId t : shLocks.get(pid)) {
                if (t.equals(tid)) {
                    has = true;
                    break;
                }
            }
        }
        if (has) {
            System.out.println("write: Transaction " + tid + " already holds a shared lock, upgrading to exclusive lock for page " + pid);
            upgradeLock(pid, tid);
        } else {
            System.out.println("write: Transaction " + tid + " does not hold any lock, acquiring exclusive lock for page " + pid);
            addExclusiveLock(pid, tid);
        }
    }

    private void addExclusiveLock(PageId pid, TransactionId tid) throws LockTimeoutException, InterruptedException {
        System.out.println("addExclusiveLock: Transaction " + tid + " is trying to acquire an exclusive lock on page " + pid);
        int time = 0;
        while (exLocks.containsKey(pid) && time < TIMEOUT) {
            System.out.println("addExclusiveLock: Waiting for existing exclusive lock to be released on page " + pid);
            try {
                Thread.sleep(10);
                time += 10;
            } catch (InterruptedException e) {
                System.out.println("addExclusiveLock: Sleep interrupted for Transaction " + tid + " on page " + pid + " - " + e);
                throw new InterruptedException("Thread interrupted while waiting for exclusive lock");
            }
        }
        if (time >= TIMEOUT) {
            System.out.println("addExclusiveLock: Timeout while waiting for exclusive lock by Transaction " + tid + " on page " + pid);
            throw new LockTimeoutException("Timeout while waiting for exclusive lock");
        }
        exLocks.put(pid, tid);
        System.out.println("addExclusiveLock: Transaction " + tid + " acquired an exclusive lock on page " + pid);
    }

    private void upgradeLock(PageId pid, TransactionId tid) throws LockTimeoutException, InterruptedException {
        System.out.println("upgradeLock: Transaction " + tid + " is trying to upgrade to an exclusive lock on page " + pid);
        int time = 0;
        while ((exLocks.containsKey(pid) || shLocks.get(pid).size() > 1) && time < TIMEOUT) {
            System.out.println("upgradeLock: Waiting for other locks to be released on page " + pid);
            try {
                Thread.sleep(10);
                time += 10;
            } catch (InterruptedException e) {
                System.out.println("upgradeLock: Sleep interrupted for Transaction " + tid + " on page " + pid + " - " + e);
                throw new InterruptedException("Thread interrupted while upgrading to exclusive lock");
            }
        }
        if (time >= TIMEOUT) {
            System.out.println("upgradeLock: Timeout while upgrading lock for Transaction " + tid + " on page " + pid);
            throw new LockTimeoutException("Timeout while upgrading lock");
        }
        shLocks.remove(pid);
        exLocks.put(pid, tid);
        System.out.println("upgradeLock: Transaction " + tid + " upgraded to an exclusive lock on page " + pid);
    }

    public void release(TransactionId tid, PageId pid) {
        System.out.println("release: Transaction " + tid + " is releasing its lock on page " + pid);
        shLocks.computeIfPresent(pid, (k, v) -> {
            v.remove(tid);
            return v.isEmpty() ? null : v;
        });
        exLocks.remove(pid, tid);
    }

    public void release(TransactionId tid) {
        System.out.println("release: Transaction " + tid + " is releasing all its locks");
        exLocks.values().remove(tid);
        Iterator<ArrayList<TransactionId>> it = shLocks.values().iterator();
        while (it.hasNext()) {
            it.next().remove(tid);
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