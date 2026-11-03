/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: C:\\Users\\Administrator\\AppData\\Local\\Android\\Sdk\\build-tools\\36.0.0\\aidl.exe -pC:\\Users\\Administrator\\AppData\\Local\\Android\\Sdk\\platforms\\android-37.0\\framework.aidl -oC:\\Users\\Administrator\\Desktop\\Ravager\\ Launcher\\app\\build\\generated\\aidl_source_output_dir\\debug\\out -IC:\\Users\\Administrator\\Desktop\\Ravager\\ Launcher\\app\\src\\main\\aidl -IC:\\Users\\Administrator\\Desktop\\Ravager\\ Launcher\\app\\src\\debug\\aidl -IC:\\Users\\Administrator\\.gradle\\caches\\9.8.2\\transforms\\dc432036ef17c398f43bf644043c7ce2\\transformed\\core-1.19.1\\aidl -IC:\\Users\\Administrator\\.gradle\\caches\\9.8.2\\transforms\\1fe6cb037a1b7936eebb9bd29ca2620e\\transformed\\versionedparcelable-1.1.1\\aidl -dC:\\Users\\ADMINI~1\\AppData\\Local\\Temp\\aidl6594056887385310872.d C:\\Users\\Administrator\\Desktop\\Ravager\\ Launcher\\app\\src\\main\\aidl\\com\\loop\\launcher\\privileged\\IPrivilegedService.aidl
 *
 * DO NOT CHECK THIS FILE INTO A CODE TREE (e.g. git, etc..).
 * ALWAYS GENERATE THIS FILE FROM UPDATED AIDL COMPILER
 * AS A BUILD INTERMEDIATE ONLY. THIS IS NOT SOURCE CODE.
 */
package com.loop.launcher.privileged;
public interface IPrivilegedService extends android.os.IInterface
{
  /** Default implementation for IPrivilegedService. */
  public static class Default implements com.loop.launcher.privileged.IPrivilegedService
  {
    /** Required by Shizuku to stop the service. */
    @Override public void destroy() throws android.os.RemoteException
    {
    }
    /** Runs a command; the first line of the answer is the exit code, the rest is the output. */
    @Override public java.lang.String exec(java.lang.String[] argv) throws android.os.RemoteException
    {
      return null;
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements com.loop.launcher.privileged.IPrivilegedService
  {
    /** Construct the stub and attach it to the interface. */
    @SuppressWarnings("this-escape")
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an com.loop.launcher.privileged.IPrivilegedService interface,
     * generating a proxy if needed.
     */
    public static com.loop.launcher.privileged.IPrivilegedService asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof com.loop.launcher.privileged.IPrivilegedService))) {
        return ((com.loop.launcher.privileged.IPrivilegedService)iin);
      }
      return new com.loop.launcher.privileged.IPrivilegedService.Stub.Proxy(obj);
    }
    @Override public android.os.IBinder asBinder()
    {
      return this;
    }
    @Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
    {
      java.lang.String descriptor = DESCRIPTOR;
      if (code >= android.os.IBinder.FIRST_CALL_TRANSACTION && code <= android.os.IBinder.LAST_CALL_TRANSACTION) {
        data.enforceInterface(descriptor);
      }
      if (code == INTERFACE_TRANSACTION) {
        reply.writeString(descriptor);
        return true;
      }
      switch (code)
      {
        case TRANSACTION_destroy:
        {
          this.destroy();
          reply.writeNoException();
          break;
        }
        case TRANSACTION_exec:
        {
          java.lang.String[] _arg0;
          _arg0 = data.createStringArray();
          java.lang.String _result = this.exec(_arg0);
          reply.writeNoException();
          reply.writeString(_result);
          break;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
      return true;
    }
    private static class Proxy implements com.loop.launcher.privileged.IPrivilegedService
    {
      private android.os.IBinder mRemote;
      Proxy(android.os.IBinder remote)
      {
        mRemote = remote;
      }
      @Override public android.os.IBinder asBinder()
      {
        return mRemote;
      }
      public java.lang.String getInterfaceDescriptor()
      {
        return DESCRIPTOR;
      }
      /** Required by Shizuku to stop the service. */
      @Override public void destroy() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_destroy, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      /** Runs a command; the first line of the answer is the exit code, the rest is the output. */
      @Override public java.lang.String exec(java.lang.String[] argv) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.lang.String _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStringArray(argv);
          boolean _status = mRemote.transact(Stub.TRANSACTION_exec, _data, _reply, 0);
          _reply.readException();
          _result = _reply.readString();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
    }
    static final int TRANSACTION_destroy = (android.os.IBinder.FIRST_CALL_TRANSACTION + 16777114);
    static final int TRANSACTION_exec = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
  }
  /** @hide */
  public static final java.lang.String DESCRIPTOR = "com.loop.launcher.privileged.IPrivilegedService";
  /** Required by Shizuku to stop the service. */
  public void destroy() throws android.os.RemoteException;
  /** Runs a command; the first line of the answer is the exit code, the rest is the output. */
  public java.lang.String exec(java.lang.String[] argv) throws android.os.RemoteException;
}
