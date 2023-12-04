package simpledb;

import java.util.concurrent.*;
import java.util.*;
import simpledb.common.*;
import simpledb.execution.*;
import simpledb.optimizer.*;
import simpledb.storage.*;
import simpledb.transaction.*;

public class LockManagerChatgp {

    private final Map<PageId, Lock> pageLocks;
    private final Map<TransactionId, Set<PageId>> transactionPages;

    public LockManagerChatgp() {
        pageLocks = new ConcurrentHashMap<>();
        transactionPages = new ConcurrentHashMap<>();
    }

    public synchronized boolean acquireLock(TransactionId tid, PageId pid, Permissions perm) {
        while (true) {
            Lock lock = pageLocks.get(pid);

            if (lock == null) {
                lock = new Lock(perm);
                pageLocks.put(pid, lock);
                lock.holders.add(tid);
                transactionPages.computeIfAbsent(tid, k -> new HashSet<>()).add(pid);
                return true;
            }

            if (lock.perm == Permissions.READ_ONLY && perm == Permissions.READ_ONLY && !lock.holders.contains(tid)) {
                lock.holders.add(tid);
                transactionPages.computeIfAbsent(tid, k -> new HashSet<>()).add(pid);
                return true;
            }

            if (lock.holders.size() == 1 && lock.holders.contains(tid)) {
                if (perm == Permissions.READ_WRITE) {
                    lock.perm = perm;
                }
                return true;
            }

            // Handle lock conflict by waiting
            try {
                wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Lock acquisition interrupted", e);
            }
        }
    }

    public synchronized void releaseLock(TransactionId tid, PageId pid) {
        Lock lock = pageLocks.get(pid);
        if (lock != null && lock.holders.contains(tid)) {
            lock.holders.remove(tid);
            if (lock.holders.isEmpty()) {
                pageLocks.remove(pid);
            }
            transactionPages.getOrDefault(tid, Collections.emptySet()).remove(pid);
            notifyAll();
        }
    }

    public synchronized Permissions getLockType(TransactionId tid, PageId pid) {
        Lock lock = pageLocks.get(pid);
        if (lock != null && lock.holders.contains(tid)) {
            return lock.perm;
        }
        return null;
    }

    public synchronized void releaseAllLocks(TransactionId tid) {
        Set<PageId> pages = transactionPages.get(tid);
        if (pages != null) {
            for (PageId pid : pages) {
                releaseLock(tid, pid);
            }
            transactionPages.remove(tid);
        }
    }

    private static class Lock {
        Permissions perm;
        Set<TransactionId> holders;

        Lock(Permissions perm) {
            this.perm = perm;
            this.holders = new HashSet<>();
        }
    }
}