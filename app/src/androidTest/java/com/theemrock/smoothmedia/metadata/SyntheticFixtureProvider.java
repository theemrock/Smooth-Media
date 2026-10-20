package com.theemrock.smoothmedia.metadata;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.Bundle;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class SyntheticFixtureProvider extends ContentProvider {
    public static final String AUTHORITY = "com.theemrock.smoothmedia.test.fixture";
    public static final String METHOD_CREATE = "create";
    public static final String METHOD_DELETE = "delete";
    public static final String EXTRA_BYTES = "bytes";
    public static final String EXTRA_PACKAGE = "package";
    public static final String EXTRA_UID = "uid";
    public static final String EXTRA_PROCESS = "process";
    public static final AtomicInteger openDescriptors = new AtomicInteger(0);

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        List<String> segments = uri.getPathSegments();
        if (segments.size() != 1) {
            throw new FileNotFoundException("Invalid synthetic fixture URI: " + uri);
        }
        File file = new File(new File(attachedContext().getFilesDir(), "synthetic-fixtures"), segments.get(0));
        if (!file.isFile()) {
            throw new FileNotFoundException("Missing synthetic fixture: " + file);
        }
        int flags = segments.get(0).startsWith("readonly-")
                ? ParcelFileDescriptor.MODE_READ_ONLY
                : ParcelFileDescriptor.MODE_READ_WRITE;
        openDescriptors.incrementAndGet();
        try {
            return ParcelFileDescriptor.open(
                    file,
                    flags,
                    new Handler(Looper.getMainLooper()),
                    pfd -> openDescriptors.decrementAndGet()
            );
        } catch (java.io.IOException error) {
            openDescriptors.decrementAndGet();
            FileNotFoundException failure = new FileNotFoundException(
                    "Cannot open synthetic fixture: " + file
            );
            failure.initCause(error);
            throw failure;
        }
    }

    @Override
    public String getType(Uri uri) {
        return "application/octet-stream";
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        String name = singleName(arg);
        File directory = new File(attachedContext().getFilesDir(), "synthetic-fixtures");
        File file = new File(directory, name);
        try {
            if (METHOD_CREATE.equals(method)) {
                byte[] bytes = extras == null ? null : extras.getByteArray(EXTRA_BYTES);
                if (bytes == null || bytes.length == 0) {
                    throw new IllegalArgumentException("Synthetic fixture bytes are required");
                }
                if (!directory.mkdirs() && !directory.isDirectory()) {
                    throw new IOException("Cannot create provider fixture directory");
                }
                try (FileOutputStream output = new FileOutputStream(file, false)) {
                    output.write(bytes);
                    output.flush();
                }
            } else if (METHOD_DELETE.equals(method)) {
                if (file.exists() && !file.delete()) {
                    throw new IOException("Cannot delete synthetic fixture");
                }
            } else {
                throw new IllegalArgumentException("Unsupported provider call: " + method);
            }
        } catch (IOException error) {
            throw new IllegalStateException("Synthetic fixture operation failed", error);
        }
        Bundle result = new Bundle();
        result.putString(EXTRA_PACKAGE, attachedContext().getPackageName());
        result.putInt(EXTRA_UID, android.os.Process.myUid());
        result.putString(EXTRA_PROCESS, android.app.Application.getProcessName());
        return result;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("not supported");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("not supported");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("not supported");
    }

    public static Uri uri(String name) {
        return Uri.parse("content://" + AUTHORITY + "/" + name);
    }

    private android.content.Context attachedContext() {
        android.content.Context context = getContext();
        if (context == null) {
            throw new IllegalStateException("Provider is not attached");
        }
        return context;
    }

    private String singleName(String arg) {
        if (arg == null || arg.isEmpty() || arg.contains("/") || arg.contains("\\") || arg.contains("..")) {
            throw new IllegalArgumentException("Invalid synthetic fixture name");
        }
        return arg;
    }

}
