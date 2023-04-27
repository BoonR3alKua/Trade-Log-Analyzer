import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Dispatches alerts in a separate thread.
 * Ensures that alert processing doesn't block log file reading.
 */
public class AlertDispatcher implements Runnable {
    private final Queue<Alert> alertQueue;
    private final List<AlertListener> listeners;
    private final ReentrantLock lock;
    private volatile boolean running;
    private Thread dispatcherThread;

    // Alert listeners for subscribers
    public interface AlertListener {
        void onAlertTriggered(Alert alert);
    }

    public AlertDispatcher() {
        this.alertQueue = new LinkedList<>();
        this.listeners = new ArrayList<>();
        this.lock = new ReentrantLock();
        this.running = false;
    }

    /**
     * Starts the alert dispatcher thread
     */
    public synchronized void start() {
        if (running) {
            System.out.println("AlertDispatcher is already running");
            return;
        }
        
        running = true;
        dispatcherThread = new Thread(this, "AlertDispatcher-Thread");
        dispatcherThread.setDaemon(false);
        dispatcherThread.start();
        System.out.println("AlertDispatcher started");
    }

    /**
     * Stops the alert dispatcher thread
     */
    public synchronized void stop() {
        if (!running) {
            System.out.println("AlertDispatcher is not running");
            return;
        }
        
        running = false;
        lock.lock();
        try {
            alertQueue.clear();
        } finally {
            lock.unlock();
        }
        if (dispatcherThread != null) {
            dispatcherThread.interrupt();
        }
        try {
            if (dispatcherThread != null) {
                dispatcherThread.join(5000); // Wait max 5 seconds
            }
        } catch (InterruptedException e) {
            System.err.println("Interrupted while stopping AlertDispatcher");
            Thread.currentThread().interrupt();
        }
        System.out.println("AlertDispatcher stopped");
    }

    /**
     * Queues an alert for processing
     */
    public void queueAlert(Alert alert) {
        lock.lock();
        try {
            alertQueue.offer(alert);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Main dispatch loop - processes alerts from the queue
     */
    @Override
    public void run() {
        System.out.println("Alert Dispatcher thread started");
        
        while (running) {
            Alert alert = null;
            
            lock.lock();
            try {
                alert = alertQueue.poll();
            } finally {
                lock.unlock();
            }

            if (alert != null) {
                try {
                    // Process the alert
                    processAlert(alert);
                    
                    // Notify all listeners
                    notifyListeners(alert);
                } catch (Exception e) {
                    System.err.println("Error processing alert: " + alert.getAlertId());
                    e.printStackTrace();
                }
            } else {
                // No alerts in queue, sleep briefly to avoid busy waiting
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    System.err.println("AlertDispatcher interrupted");
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        // Process remaining alerts before shutting down
        processPendingAlerts();
        System.out.println("Alert Dispatcher thread stopped");
    }

    /**
     * Processes a single alert
     */
    private void processAlert(Alert alert) {
        System.out.println("\n" + separator());
        System.out.println("PROCESSING " + alert.getSeverity() + " ALERT");
        System.out.println(separator());
        System.out.println(alert);
        System.out.println(separator() + "\n");
        
        // Simulate alert processing 
        simulateAlertNotification(alert);
    }

    private String separator() {
        StringBuilder separator = new StringBuilder(80);
        for (int index = 0; index < 80; index++) {
            separator.append('=');
        }
        return separator.toString();
    }

    /**
     * Simulates sending alert notification
     */
    private void simulateAlertNotification(Alert alert) {
        try {
            // Simulate some processing time
            Thread.sleep(500);
            
            System.out.println("[NOTIFICATION] Alert " + alert.getAlertId() + 
                             " (" + alert.getSeverity() + ") dispatched");
            
        } catch (InterruptedException e) {
            System.err.println("Interrupted during alert notification");
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Processes any remaining alerts in the queue
     */
    private void processPendingAlerts() {
        Alert alert;
        lock.lock();
        try {
            while ((alert = alertQueue.poll()) != null) {
                try {
                    processAlert(alert);
                } catch (Exception e) {
                    System.err.println("Error processing pending alert: " + alert.getAlertId());
                    e.printStackTrace();
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Registers an alert listener
     */
    public void addListener(AlertListener listener) {
        lock.lock();
        try {
            listeners.add(listener);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Removes an alert listener
     */
    public void removeListener(AlertListener listener) {
        lock.lock();
        try {
            listeners.remove(listener);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Notifies all listeners about an alert
     */
    private void notifyListeners(Alert alert) {
        List<AlertListener> listenersCopy;
        lock.lock();
        try {
            listenersCopy = new ArrayList<>(listeners);
        } finally {
            lock.unlock();
        }
        
        for (AlertListener listener : listenersCopy) {
            try {
                listener.onAlertTriggered(alert);
            } catch (Exception e) {
                System.err.println("Error notifying listener");
                e.printStackTrace();
            }
        }
    }

    /**
     * Returns the number of alerts in the queue
     */
    public int getQueueSize() {
        lock.lock();
        try {
            return alertQueue.size();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Checks if the dispatcher is running
     */
    public boolean isRunning() {
        return running;
    }
}
