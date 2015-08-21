package com.ecache.persistent;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.ecache.Configuration;
import com.ecache.ECache;
import com.ecache.Element;
import com.ecache.persistent.disk.FileReaderWriter;
import com.ecache.utils.ThreadPoolUtil;

public class PersistentFactory {

  private ExecutorService createExecutor;
  private ScheduledExecutorService scheduler;
  private static FileReaderWriter readWrite;
  
  public PersistentFactory(Configuration config) {
    readWrite = new FileReaderWriter(config.getRootPath(), config.getSpace());
    createExecutor = ThreadPoolUtil.createExecutor();
    initFlushScheduler();
  }

  public <V, K> void addEvent(final Event event, final Element<K, V> e) {
    try {
      createExecutor.execute(new Thread(new Runnable() {
        @Override
        public void run() {
          event.handle(e);
        }
      })); 
    } catch(RejectedExecutionException ex) {
      ECache.logger.error("Thread Pool is full. Please change the cache service.", ex);
    }
  }
  
  public <K, V> void reload(Map<K, Element<K, V>>  caches) {
    List<Element<K, V>> data = readWrite.readAll();
    for(Element<K, V> e: data) {
      caches.put(e.getKey(), e);
    }
  }

  public void destory() {
    scheduler.shutdown();
    createExecutor.shutdown();
    while(true) {
      if(scheduler.isTerminated() && createExecutor.isTerminated()) {
        break;
      }
    }
    readWrite.flush();
  }
  
  private void initFlushScheduler() {
    scheduler = ThreadPoolUtil.createScheduledExecutor();
    scheduler.scheduleWithFixedDelay(new Thread(new Runnable(){
      @Override
      public void run() {
        Event.FLUSH.handle(null);
      }
    }), 1, 1, TimeUnit.MINUTES);
  }
  
  public enum Event {
    PUT {
      @Override <K, V> void handle(Element<K, V> e) {
        readWrite.add(e.getKey(), e);
      }
    },
    REMOVE {
      @Override <K, V> void handle(Element<K, V> e) {
        readWrite.remove(e.getKey());
      }
    },
    CLEAR {
      @Override <K, V> void handle(Element<K, V> e) {
        readWrite.clear();
      }      
    },
    FLUSH {
      @Override <K, V> void handle(Element<K, V> e) {
        readWrite.flush();
      }      
    };
    abstract <K, V> void handle(Element<K, V> e);
  }
  
}
