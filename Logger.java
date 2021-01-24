package com.example.myapplication;

import android.annotation.SuppressLint;
import android.util.Log;
import java.util.*;

public class Logger {
    private static final String DEFAULT_TAG = "tensorflow"; //identify source of log message
    private int DEFAULT_MIN_LOG_LEVEL = Log.DEBUG;

    // Classes to be ignored when do stack trace
    private static final Set<String> IGNORED_CLASS_NAMES;
    static {
        IGNORED_CLASS_NAMES = new HashSet<String>(3);
        IGNORED_CLASS_NAMES.add("dalvik.system.VMStack");
        IGNORED_CLASS_NAMES.add("java.lang.Thread");
        IGNORED_CLASS_NAMES.add(Logger.class.getCanonicalName());
    }

    private String tag;
    private String messagePrefix;
    private int minLogLevel = DEFAULT_MIN_LOG_LEVEL;

    //create logger using message prefix, clazz is class used as message prefix
    public Logger(final Class<?> clazz)
    {
        this(clazz.getSimpleName());
    }

    // create logger using specified message prefix
    public Logger(final String messagePrefix){
        this(DEFAULT_TAG, messagePrefix);
    }

    //create logger using caller class name as message prefix
    public Logger(){
        this(DEFAULT_TAG, null);
    }

    public Logger(final int minLogLevel){
        this(DEFAULT_TAG, null);
        this.minLogLevel = minLogLevel;
    }

    public Logger(final String tag, final String messagePrefix){
        this.tag = tag;
        final String prefix;
        if (messagePrefix == null)
            prefix= getCallerSimpleName();
        else
            prefix= messagePrefix;

        if (prefix.length()>0)
            this.messagePrefix=prefix+ ": ";
        else
            this.messagePrefix=prefix;
    }

    public void setMinLogLevel(final int minLogLevel){
        this.minLogLevel = minLogLevel;
    }

    public boolean isLoggable(final int logLevel){
        boolean results = logLevel >= minLogLevel || Log.isLoggable(tag, logLevel);
        return results;
    }

    //call caller simple name
    private String getCallerSimpleName(){
        //get current callstack
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();

        for (StackTraceElement element: stackTrace) {
            final String className = element.getClassName();
            if (!IGNORED_CLASS_NAMES.contains((className))) {
                //if is not complex class name/package
                String[] classParts = className.split("\\.");
                return classParts[classParts.length - 1];
            }
        }
        return Logger.class.getSimpleName();
    }
    private String toMessage(final String format, final Object... args) {
        return messagePrefix + (args.length > 0 ? String.format(format, args) : format);
    }
    @SuppressLint("LogTagMismatch")
    public void v(final String format, final Object... args) {
        if (isLoggable(Log.VERBOSE)) {
            Log.v(tag, toMessage(format, args));
        }
    }

    @SuppressLint("LogTagMismatch")
    public void v(final Throwable t, final String format, final Object... args) {
        if (isLoggable(Log.VERBOSE)) {
            Log.v(tag, toMessage(format, args), t);
        }
    }

    @SuppressLint("LogTagMismatch")
    public void d(final String format, final Object... args) {
        if (isLoggable(Log.DEBUG)) {
            Log.d(tag, toMessage(format, args));
        }
    }

    @SuppressLint("LogTagMismatch")
    public void d(final Throwable t, final String format, final Object... args) {
        if (isLoggable(Log.DEBUG)) {
            Log.d(tag, toMessage(format, args), t);
        }
    }

    @SuppressLint("LogTagMismatch")
    public void i(final String format, final Object... args) {
        if (isLoggable(Log.INFO)) {
            Log.i(tag, toMessage(format, args));
        }
    }

    @SuppressLint("LogTagMismatch")
    public void i(final Throwable t, final String format, final Object... args) {
        if (isLoggable(Log.INFO)) {
            Log.i(tag, toMessage(format, args), t);
        }
    }

    @SuppressLint("LogTagMismatch")
    public void w(final String format, final Object... args) {
        if (isLoggable(Log.WARN)) {
            Log.w(tag, toMessage(format, args));
        }
    }

    @SuppressLint("LogTagMismatch")
    public void w(final Throwable t, final String format, final Object... args) {
        if (isLoggable(Log.WARN)) {
            Log.w(tag, toMessage(format, args), t);
        }
    }

    @SuppressLint("LogTagMismatch")
    public void e(final String format, final Object... args) {
        if (isLoggable(Log.ERROR)) {
            Log.e(tag, toMessage(format, args));
        }
    }

    @SuppressLint("LogTagMismatch")
    public void e(final Throwable t, final String format, final Object... args) {
        if (isLoggable(Log.ERROR)) {
            Log.e(tag, toMessage(format, args), t);
        }
    }
}
