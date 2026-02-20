/*
 * MpStyle s.r.l. 2020
 * Feed custom log utility class.
 */
package mpstyle.log;

import mpstyle.util.MpStyleUtil;
import org.apache.ofbiz.base.component.ComponentConfig;
import org.apache.ofbiz.base.component.ComponentException;
import org.apache.ofbiz.base.util.Debug;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HashSet;
import java.util.Set;

/**
 *
 * @author equake58
 */
public class FeedLogger {
    
    public static String module = FeedLogger.class.getName();
    
    private final static String MP_COOMPONENT = "mpstyle";
    private final static String LOGDIR = "LOG";
    private final static String LOG_FILE_PREFIX = "FEED_";
    private final static String LOG_FILE_EXT = ".log";
    private final static String INFO_MSG_PREFIX = "[ I ] ";
    private final static String WARN_MSG_PREFIX = "[ W ] ";
    private final static String ERR_MSG_PREFIX = "[ E ] ";
    private static Path logFile = null;
    
    //constructor 1
    public FeedLogger(String tenantId, String logBaseFileName) {

        String componentPath = null;
        String tenantLogDirStr = null;
        boolean tenantLogDirExists = true;
        boolean mainLogDirExists = true;

        try {

         componentPath = ComponentConfig.getRootLocation(MP_COOMPONENT);

        }catch(ComponentException ce){
         Debug.logError(ce, module);
        }

        String componentLogDirPathStr = null;
        Path componentLogDirPath = null;

        if(componentPath.endsWith("/")) {
          componentLogDirPathStr = componentPath + LOGDIR;
          componentLogDirPath = Paths.get(componentLogDirPathStr);
        }else{
          componentLogDirPathStr = componentPath + "/" + LOGDIR;
          componentLogDirPath = Paths.get(componentLogDirPathStr);
        }

        tenantLogDirStr = componentLogDirPathStr + "/" + tenantId;
        Path tenantLogDirPath = Paths.get(tenantLogDirStr);

        if(checkFileDirNotExists(tenantLogDirPath)) {
          tenantLogDirExists = createNewDir(tenantLogDirPath);
        }


        /* if the tenant log directory exists, then create a log file for now date
           if does not exists yet; if the file for actual date already exists, then
           we will use that for logging.
           The file name is MM_YYYYMMDD.log
        */
        if(tenantLogDirExists) {

          String logFilePathStr = tenantLogDirStr + "/" + MpStyleUtil.getFileName(LOG_FILE_PREFIX, logBaseFileName, LOG_FILE_EXT);
          Debug.logWarning("log file path: "+logFilePathStr, module);
          Path logFilePath = Paths.get(logFilePathStr);

          //check if the file does not exists
          if(checkFileDirNotExists(logFilePath)) {
            this.logFile = createNewFile(logFilePath);

            try {
              Files.write(this.logFile, getFileHeaderMessage().getBytes(), StandardOpenOption.APPEND);
            }catch(IOException ioe) {
              Debug.logError(ioe, module);
            }

          }else{
            /*if the file already exists, set the reference to it or
              NullPointerExceptionError will be thrown */
            if(this.logFile == null) {
              this.logFile = logFilePath;
            }

          }

        }

    } //end class constructor 1 
    
    //construcotr 2: uses a log directory path instead of a component path
    public FeedLogger(String tenantId, String logBaseFileName, String logDirectoryPath) {

        String componentPath = null;
        String tenantLogDirStr = null;
        boolean tenantLogDirExists = true;
        boolean mainLogDirExists = true;
        
        String componentLogDirPathStr = null;
        Path componentLogDirPath = null;
        
        if(logDirectoryPath != null) {
            
            componentPath = logDirectoryPath;
            componentLogDirPathStr = componentPath;
            componentLogDirPath = Paths.get(componentLogDirPathStr);
            
        }else{

            try {

                componentPath = ComponentConfig.getRootLocation(MP_COOMPONENT);

            }catch(ComponentException ce){
             Debug.logError(ce, module);
             return;
            }
            
            if(componentPath.endsWith("/")) {
                componentLogDirPathStr = componentPath + LOGDIR;
                componentLogDirPath = Paths.get(componentLogDirPathStr);
              }else{
                componentLogDirPathStr = componentPath + "/" + LOGDIR;
                componentLogDirPath = Paths.get(componentLogDirPathStr);
              }

        }
     
        tenantLogDirStr = componentLogDirPathStr + "/" + tenantId;
        Path tenantLogDirPath = Paths.get(tenantLogDirStr);

        if(checkFileDirNotExists(tenantLogDirPath)) {
          tenantLogDirExists = createNewDir(tenantLogDirPath);
        }


        /* if the tenant log directory exists, then create a log file for now date
           if does not exists yet; if the file for actual date already exists, then
           we will use that for logging.
           The file name is MM_YYYYMMDD.log
        */
        if(tenantLogDirExists) {

          String logFilePathStr = tenantLogDirStr + "/" + MpStyleUtil.getFileName(LOG_FILE_PREFIX, logBaseFileName, LOG_FILE_EXT);
          Debug.logWarning("log file path: "+logFilePathStr, module);
          Path logFilePath = Paths.get(logFilePathStr);

          //check if the file does not exists
          if(checkFileDirNotExists(logFilePath)) {
            this.logFile = createNewFile(logFilePath);

            try {
              Files.write(this.logFile, getFileHeaderMessage().getBytes(), StandardOpenOption.APPEND);
            }catch(IOException ioe) {
              Debug.logError(ioe, module);
            }

          }else{
            /*if the file already exists, set the reference to it or
              NullPointerExceptionError will be thrown */
            if(this.logFile == null) {
              this.logFile = logFilePath;
            }

          }

        }

} //end class constructor 2
    
    
    
  /** Method that write a log message for a "INFO" log level.
      @param msg the message to write into the log file
    */
  public void logInfo(String msg) {

    msg = INFO_MSG_PREFIX + msg + "\n";

    try {
      Files.write(this.logFile, msg.getBytes(), StandardOpenOption.APPEND);
    }catch(IOException ioe) {
        Debug.logError(ioe, module);
    }


  }

  /** Method that write a log message for a "WARNING" log level.
      @param msg the message to write into the log file
    */
  public void logWarning(String msg) {

    msg = WARN_MSG_PREFIX + msg + "\n";

    try {
      Files.write(this.logFile, msg.getBytes(), StandardOpenOption.APPEND);
    }catch(IOException ioe) {
        Debug.logError(ioe, module);
    }

  }

  /** Method that write a log message for a "ERROR" log level.
      @param msg the message to write into the log file
    */
  public void logError(String msg) {

    msg = ERR_MSG_PREFIX + msg + "\n";

    try {
      Files.write(this.logFile, msg.getBytes(), StandardOpenOption.APPEND);
    }catch(IOException ioe) {
        Debug.logError(ioe, module);
    }


  }
  
  /** Method that creates a new directory with 666 Posix file permissions.
    * @param dirpath of the directory to create
    * @return true if the directory has been created; false otherwise;
    */
  public static boolean makeDir(Path dirpath) {

    Path newDir = null;

    Set<PosixFilePermission> permSet = new HashSet<>();
    //add rw-rw-rw- (666) for the dir
    permSet.add(PosixFilePermission.OWNER_READ);
    permSet.add(PosixFilePermission.OWNER_WRITE);
    permSet.add(PosixFilePermission.OWNER_EXECUTE);
    permSet.add(PosixFilePermission.GROUP_READ);
    permSet.add(PosixFilePermission.GROUP_WRITE);
    permSet.add(PosixFilePermission.GROUP_EXECUTE);
    permSet.add(PosixFilePermission.OTHERS_READ);
    permSet.add(PosixFilePermission.OTHERS_WRITE);
    permSet.add(PosixFilePermission.OTHERS_EXECUTE);

    FileAttribute<Set<PosixFilePermission>> filePermission = PosixFilePermissions.asFileAttribute(permSet);

    try {
      newDir = Files.createDirectories(dirpath, filePermission);
    }catch(FileAlreadyExistsException faee){
      Debug.logWarning("Directory "+dirpath+" already exists", module);
      return true;
    }catch(UnsupportedOperationException uoe) {
      Debug.logError(uoe, module);
      return false;
    }catch(SecurityException se) {
      Debug.logError(se, module);
      return false;
    }catch(IOException ioe) {
      Debug.logError("Error in creating directory: " + dirpath + "." + ioe, module);
      return false;
    }

    Debug.logWarning("create directory:"+newDir, module);

    return Files.exists(newDir, new LinkOption[]{LinkOption.NOFOLLOW_LINKS});

  }
    
    
    /* ############################# PRIVATE METHODS ############################ */

  /** Method that checks if a file or directory exists.
    * @param path the abstract pathname of the directory to checks
    * @return true if directory exists; false otherwise.
    */
  private boolean checkFileDirExists(Path path) {

    return Files.exists(path, new LinkOption[]{LinkOption.NOFOLLOW_LINKS});

  }

  /** Method that checks if a file or directory does not exists.
    * @param path the abstract pathname of the directory to checks
    * @return true if directory does not exists; false otherwise.
    */
  private boolean checkFileDirNotExists(Path path) {

    return Files.notExists(path, new LinkOption[]{LinkOption.NOFOLLOW_LINKS});

  }

  /** Method that creates a new directory with 666 Posix file permissions.
    * @param path of the directory to create
    * @return true if the directory has been created; false otherwise;
    */
  private boolean createNewDir(Path dirpath) {

    Path newDir = null;

    Set<PosixFilePermission> permSet = new HashSet<>();
    //add rw-rw-rw- (666) for the dir
    permSet.add(PosixFilePermission.OWNER_READ);
    permSet.add(PosixFilePermission.OWNER_WRITE);
    permSet.add(PosixFilePermission.OWNER_EXECUTE);
    permSet.add(PosixFilePermission.GROUP_READ);
    permSet.add(PosixFilePermission.GROUP_WRITE);
    permSet.add(PosixFilePermission.GROUP_EXECUTE);
    permSet.add(PosixFilePermission.OTHERS_READ);
    permSet.add(PosixFilePermission.OTHERS_WRITE);
    permSet.add(PosixFilePermission.OTHERS_EXECUTE);

    FileAttribute<Set<PosixFilePermission>> filePermission = PosixFilePermissions.asFileAttribute(permSet);

    try {
      newDir = Files.createDirectories(dirpath, filePermission);
    }catch(FileAlreadyExistsException faee){
      Debug.logWarning("Directory "+dirpath+" already exists", module);
      return true;
    }catch(UnsupportedOperationException uoe) {
      Debug.logError(uoe, module);
      return false;
    }catch(SecurityException se) {
      Debug.logError(se, module);
      return false;
    }catch(IOException ioe) {
      Debug.logError("Error in creating directory: " + dirpath + "." + ioe, module);
      return false;
    }

    Debug.logWarning("create directory:"+newDir, module);

    return Files.exists(newDir, new LinkOption[]{LinkOption.NOFOLLOW_LINKS});

  }

  /** Method that creates a new file with 666 Posix file permissions.
    * @param filepath of the file to create
    * @return the Path to the created file; null if errors occours;
    */
  private Path createNewFile(Path filepath) {

    Path newFile = null;

    Set<PosixFilePermission> permSet = new HashSet<>();
    //add rw-rw-rw- (666) for the dir
    permSet.add(PosixFilePermission.OWNER_READ);
    permSet.add(PosixFilePermission.OWNER_WRITE);
    permSet.add(PosixFilePermission.OWNER_EXECUTE);
    permSet.add(PosixFilePermission.GROUP_READ);
    permSet.add(PosixFilePermission.GROUP_WRITE);
    permSet.add(PosixFilePermission.GROUP_EXECUTE);
    permSet.add(PosixFilePermission.OTHERS_READ);
    permSet.add(PosixFilePermission.OTHERS_WRITE);
    permSet.add(PosixFilePermission.OTHERS_EXECUTE);
    FileAttribute<Set<PosixFilePermission>> filePermission = PosixFilePermissions.asFileAttribute(permSet);

    try {
      newFile = Files.createFile(filepath, filePermission);
    }catch(FileAlreadyExistsException faee){
      Debug.logWarning("File "+filepath+" already exists", module);
      return null;
    }catch(UnsupportedOperationException uoe) {
      Debug.logError(uoe, module);
      return null;
    }catch(SecurityException se) {
      Debug.logError(se, module);
      return null;
    }catch(IOException ioe) {
      Debug.logError("Error in creating file: " + filepath + "." + ioe, module);
      return null;
    }

    return newFile;

  }
  
  
  /** Return a header string to write into the log file, the first time is created.
    * @return a header string composed by now date and time
    */
  private String getFileHeaderMessage() {

    String header_msg = "########## " + MpStyleUtil.getNowDateTimeString() + " ##########\n";

    return header_msg;

  }
  
    
} //end class
