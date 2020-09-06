package org.jessies.os;

/**
 * Home to all the native methods needed to implement the POSIX-related classes in org.jessies.os.
 * As well as gathering everything into one C++ class and one library, this gives us an extra level of indirection.
 * User code can now refer to Posix (and friends) without causing the JNI library to be loaded unless it also *invokes* a method.
 * 
 * See "org_jessies_os_PosixJNI.cpp" for the native methods' implementations.
 */
class PosixJNI {
    static { e.util.FileUtilities.loadNativeLibrary("posix"); }
    
    static native int get_EXIT_FAILURE();
    
    static native int get_SEEK_CUR();
    static native int get_SEEK_END();
    static native int get_SEEK_SET();
    
    static native int get_R_OK();
    static native int get_W_OK();
    static native int get_X_OK();
    static native int get_F_OK();
    
    static native int get_S_IFMT();
    static native int get_S_IFBLK();
    static native int get_S_IFCHR();
    static native int get_S_IFIFO();
    static native int get_S_IFREG();
    static native int get_S_IFDIR();
    static native int get_S_IFLNK();
    static native int get_S_IFSOCK();
    
    static native int get_S_ISUID();
    static native int get_S_ISGID();
    
    static native int get_SIGABRT();
    static native int get_SIGALRM();
    static native int get_SIGBUS();
    static native int get_SIGCHLD();
    static native int get_SIGCONT();
    static native int get_SIGFPE();
    static native int get_SIGHUP();
    static native int get_SIGILL();
    static native int get_SIGINT();
    static native int get_SIGKILL();
    static native int get_SIGPIPE();
    static native int get_SIGQUIT();
    static native int get_SIGSEGV();
    static native int get_SIGSTOP();
    static native int get_SIGTERM();
    static native int get_SIGTSTP();
    static native int get_SIGTTIN();
    static native int get_SIGTTOU();
    static native int get_SIGUSR1();
    static native int get_SIGUSR2();
    static native int get_SIGPROF();
    static native int get_SIGSYS();
    static native int get_SIGTRAP();
    static native int get_SIGURG();
    static native int get_SIGXCPU();
    static native int get_SIGXFSZ();
    
    static native int get_O_CREAT();
    static native int get_O_EXCL();
    static native int get_O_NOCTTY();
    static native int get_O_TRUNC();
    
    static native int get_O_APPEND();
    
    static native int get_O_RDONLY();
    static native int get_O_RDWR();
    static native int get_O_WRONLY();
    
    static native int get_WCONTINUED();
    static native int get_WNOHANG();
    static native int get_WUNTRACED();
    
    static native int get_E2BIG();
    static native int get_EACCES();
    static native int get_EADDRINUSE();
    static native int get_EADDRNOTAVAIL();
    static native int get_EAFNOSUPPORT();
    static native int get_EAGAIN();
    static native int get_EALREADY();
    static native int get_EBADF();
    static native int get_EBADMSG();
    static native int get_EBUSY();
    static native int get_ECANCELED();
    static native int get_ECHILD();
    static native int get_ECONNABORTED();
    static native int get_ECONNREFUSED();
    static native int get_ECONNRESET();
    static native int get_EDEADLK();
    static native int get_EDESTADDRREQ();
    static native int get_EDOM();
    static native int get_EDQUOT();
    static native int get_EEXIST();
    static native int get_EFAULT();
    static native int get_EFBIG();
    static native int get_EHOSTUNREACH();
    static native int get_EIDRM();
    static native int get_EILSEQ();
    static native int get_EINPROGRESS();
    static native int get_EINTR();
    static native int get_EINVAL();
    static native int get_EIO();
    static native int get_EISCONN();
    static native int get_EISDIR();
    static native int get_ELOOP();
    static native int get_EMFILE();
    static native int get_EMLINK();
    static native int get_EMSGSIZE();
    static native int get_EMULTIHOP();
    static native int get_ENAMETOOLONG();
    static native int get_ENETDOWN();
    static native int get_ENETRESET();
    static native int get_ENETUNREACH();
    static native int get_ENFILE();
    static native int get_ENOBUFS();
    static native int get_ENODEV();
    static native int get_ENOENT();
    static native int get_ENOEXEC();
    static native int get_ENOLCK();
    static native int get_ENOLINK();
    static native int get_ENOMEM();
    static native int get_ENOMSG();
    static native int get_ENOPROTOOPT();
    static native int get_ENOSPC();
    static native int get_ENOSYS();
    static native int get_ENOTCONN();
    static native int get_ENOTDIR();
    static native int get_ENOTEMPTY();
    static native int get_ENOTSOCK();
    static native int get_ENOTSUP();
    static native int get_ENOTTY();
    static native int get_ENXIO();
    static native int get_EOPNOTSUPP();
    static native int get_EOVERFLOW();
    static native int get_EPERM();
    static native int get_EPIPE();
    static native int get_EPROTO();
    static native int get_EPROTONOSUPPORT();
    static native int get_EPROTOTYPE();
    static native int get_ERANGE();
    static native int get_EROFS();
    static native int get_ESPIPE();
    static native int get_ESRCH();
    static native int get_ESTALE();
    static native int get_ETIMEDOUT();
    static native int get_ETXTBSY();
    static native int get_EWOULDBLOCK();
    static native int get_EXDEV();
    
    // These are all macros, so we have to manually mangle their names for the benefit of the JNI.
    static native boolean WCoreDump(int status);
    static native int WExitStatus(int status);
    static native boolean WIfContinued(int status);
    static native boolean WIfExited(int status);
    static native boolean WIfSignaled(int status);
    static native boolean WIfStopped(int status);
    static native int WStopSig(int status);
    static native int WTermSig(int status);
    
    static native int close(int fd);
    static native int getpid();
    static native Passwd getpwnam(String name);
    static native Passwd getpwuid(int uid);
    static native int kill(int pid, int signal);
    static native int killpg(int group, int signal);
    static native int read(int fd, byte[] buffer, int bufferOffset, int byteCount);
    static native String strerror(int errno);
    static native int tcgetpgrp(int fd);
    static native int waitpid(int pid, WaitStatus status, int flags);
    static native int write(int fd, byte[] buffer, int bufferOffset, int byteCount);
}
