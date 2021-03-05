package SimpleHttpServer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class DefaultThreadPool<Job extends Runnable> implements ThreadPool<Job> {
    //最大工作者线程数
    private static final int MAX_WORKER_NUMBERS = 10;
    //默认
    private static final int DEFAULT_WORKER_NUMBERS = 5;
    //最小
    private static final int MIN_WORKER_NUMBERS = 1;
    //Job列表，维护需要执行的Job
    private final LinkedList<Job> jobs = new LinkedList<Job>();
    //工作者列表
    private final List<Worker> workers = Collections.synchronizedList(new ArrayList<Worker>());
    //工作者线程数量
    private int workerNum = DEFAULT_WORKER_NUMBERS;
    //用于生成工作者编号
    private AtomicLong threadNum = new AtomicLong();

    public DefaultThreadPool() 
    {
        initializeWorkers(DEFAULT_WORKER_NUMBERS);
    }

    public DefaultThreadPool(int num) 
    {
        workerNum = num > MAX_WORKER_NUMBERS ? MAX_WORKER_NUMBERS : num < MIN_WORKER_NUMBERS ? MIN_WORKER_NUMBERS : num;
        initializeWorkers(workerNum);
    }

    @Override
    public void execute(Job job) 
    {
        if (job != null) 
        {
            //添加一个Job，并通知一个工作者线程
            synchronized (jobs) 
            {
                jobs.add(job);
                jobs.notify();
            }
        }
    }

    @Override
    public void shutdown() 
    {
        for (Worker worker : workers)
        {
            worker.shutdown();
        }
    }

    @Override
    public void addWorkers(int num) 
    {
        //限制工作者线程数量不超过最大值
        synchronized (jobs)
        {
            if (num + workerNum > MAX_WORKER_NUMBERS)
                num = MAX_WORKER_NUMBERS - workerNum;
            initializeWorkers(num);
            workerNum += num;
        }

    }

    @Override
    public void removeWorker(int num) 
    {
        if (num > workerNum)
            throw new IllegalArgumentException("beyong workerNum");
        int count = 0;
        while (count < num) 
        {
            //先在链表中删除线程，再停止该线程
            Worker worker = workers.get(count);
            if (workers.remove(worker))
            {
                worker.shutdown();
                count++;
            }    
        }
        workerNum -= count;
    }

    @Override
    public int getJobSize() 
    {
        return jobs.size();
    }

    private void initializeWorkers(int num) 
    {
        //新建线程，加入链表并启动
        for (int i = 0; i < num; i++) 
        {
            Worker worker = new Worker();
            workers.add(worker);
            Thread thread = new Thread(worker, "ThreadPool-Worker-" + threadNum.incrementAndGet());
            thread.start();
        }
    }

    /**
     * Worker
     */
    class Worker implements Runnable 
    {
        //运行状态
        private volatile boolean running = true;

        @Override
        public void run() 
        {
            Job job = null;
            while (running) 
            {
                synchronized (jobs) 
                {
                    //如果job列表为空，则wait
                    while (jobs.isEmpty()) 
                    {
                        try 
                        {
                            jobs.wait();
                        } 
                        catch (InterruptedException e) 
                        {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                    //不为空，取出一个job
                    job = jobs.removeFirst();
                }
                if (job != null) 
                {
                    try 
                    {
                        job.run();
                    } catch (Exception e) 
                    {

                    }
                }
            }
        }

        public void shutdown() 
        {
            running = false;
        }
    }
}