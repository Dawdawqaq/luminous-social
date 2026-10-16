package com.luminous.api.lumi;

import java.io.*;
import java.util.concurrent.*;

/** 请求取消和资源注册可发生在不同线程；先取消后注册也必须立即关闭资源。 */
public final class LumiStreamCall {
    private boolean cancelled;
    private Future<?> pending;
    private InputStream input;
    public synchronized boolean cancelled(){return cancelled;}
    public synchronized void pending(Future<?> value){pending=value;if(cancelled)value.cancel(true);}
    public synchronized void input(InputStream value){input=value;if(cancelled)closeInput();}
    public synchronized void cancel(){cancelled=true;if(pending!=null)pending.cancel(true);closeInput();}
    private void closeInput(){if(input!=null)try{input.close();}catch(IOException ignored){}}
}
