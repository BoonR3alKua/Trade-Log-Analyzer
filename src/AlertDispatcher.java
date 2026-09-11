import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/** Single-consumer alert delivery with bounded buffering and graceful draining. */
public class AlertDispatcher implements Runnable {
    public interface AlertListener {
        void onAlertTriggered(Alert alert);
    }

    private final BlockingQueue<Alert> queue = new ArrayBlockingQueue<>(1024);
    private final CopyOnWriteArrayList<AlertListener> listeners = new CopyOnWriteArrayList<>();
    private volatile boolean running;
    private boolean accepting;
    private Thread worker;

    public synchronized void start() {
        if (worker != null) return;
        accepting = true;
        running = true;
        worker = new Thread(this, "AlertDispatcher-Thread");
        worker.start();
    }

    /** Stops accepting work and waits for every accepted alert and callback. */
    public void stop() {
        Thread thread;
        synchronized (this) {
            accepting = false;
            thread = worker;
        }
        if (thread == null || thread == Thread.currentThread()) return;
        boolean interrupted = false;
        while (thread.isAlive()) {
            try { thread.join(); }
            catch (InterruptedException e) { interrupted = true; }
        }
        if (interrupted) Thread.currentThread().interrupt();
    }

    /** Explicit rejection avoids silently dropping alerts or unbounded memory use. */
    public synchronized void queueAlert(Alert alert) {
        Objects.requireNonNull(alert, "alert");
        if (!accepting) throw new IllegalStateException("Dispatcher is not accepting alerts");
        if (!queue.offer(alert)) throw new IllegalStateException("Alert queue is full (1024 alerts)");
    }

    private synchronized boolean hasWork() { return accepting || !queue.isEmpty(); }

    @Override
    public void run() {
        try {
            while (hasWork()) {
                Alert alert;
                try { alert = queue.poll(100, TimeUnit.MILLISECONDS); }
                catch (InterruptedException e) {
                    synchronized (this) { accepting = false; }
                    continue;
                }
                if (alert == null) continue;
                System.out.println("\n" + alert);
                for (AlertListener listener : listeners) {
                    try { listener.onAlertTriggered(alert); }
                    catch (Exception e) {
                        System.err.println("Listener failed for " + alert.getAlertId() + ": " + e);
                    }
                }
            }
        } finally {
            synchronized (this) { accepting = false; }
            running = false;
        }
    }

    public void addListener(AlertListener listener) { listeners.add(Objects.requireNonNull(listener)); }
    public void removeListener(AlertListener listener) { listeners.remove(listener); }
    public int getQueueSize() { return queue.size(); }
    public boolean isRunning() { return running; }
}
