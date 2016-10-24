#! /bin/sh
#  /etc/init.d/mydaemon

### BEGIN INIT INFO
# Provides:          mydaemon
# Required-Start:    $remote_fs $syslog
# Required-Stop:     $remote_fs $syslog
# Short-Description: Starts the MyDaemon service
# Description:       This file is used to start the daemon
#                    and should be placed in /etc/init.d
### END INIT INFO


NAME="jars"
DESC="hanvadaemon service"

# The path to Jsvc
EXEC="/usr/bin/jsvc"

# The path to the folder containing MyDaemon.jar
FILE_PATH="/home/administrator1/$NAME"

# The path to the folder containing the java runtime
JAVA_HOME="/usr/lib/jvm/java-7-openjdk-amd64"

# Our classpath including our jar file and the Apache Commons Daemon library
CLASS_PATH="$FILE_PATH/nva.jar:$FILE_PATH/commons-daemon-1.0.15.jar:$FILE_PATH/*"
echo $CLASS_PATH


# The fully qualified name of the class to execute
CLASS="nvadaemon.src.main.java.com.company.NvaDaemon"

# Any command line arguments to be passed to the our Java Daemon implementations init() method 
ARGS="myArg1 myArg2 myArg3"

#The user to run the daemon as
USER="administrator1"

# The file that will contain our process identification number (pid) for other scripts/programs that need to access it.
PID="$FILE_PATH/$NAME.pid"


# System.out writes to this file...
LOG_OUT="$FILE_PATH/$NAME.out"

# System.err writes to this file...
LOG_ERR="$FILE_PATH/$NAME.err"

#echo $EXEC -home $JAVA_HOME -cp $CLASS_PATH -user $USER -outfile $LOG_OUT -errfile $LOG_ERR -pidfile $PID $1 $CLASS $ARGS



 jsvc_exec()
{   
     cd $FILE_PATH
   $EXEC -home $JAVA_HOME -cp $CLASS_PATH -user $USER -outfile $LOG_OUT -errfile $LOG_ERR -pidfile $PID $1 $CLASS $ARGS -debug -verbose
}

case "$1" in
     start)  
         if [ -f "$PID" ]; then
           echo "Service Already Running"
           exit 1  
         fi
  

         echo "Starting the $DESC..."        
         
         # Start the service
         jsvc_exec
         
         echo "The $DESC has started."
     ;;
     stop)
         if [ !  -f "$PID" ]; then
           echo "Service Not Running"
           exit 1
         fi
 

         echo "Stopping the $DESC..."
         
         # Stop the service
         jsvc_exec "-stop"       
         
         echo "The $DESC has stopped."
     ;;
     restart)
         if [ -f "$PID" ]; then
             
             echo "Restarting the $DESC..."
             
             # Stop the service
             jsvc_exec "-stop"
             
             # Start the service
             jsvc_exec
             
             echo "The $DESC has restarted."
         else
             echo "Daemon not running, no action taken"
             exit 1
         fi
             ;;
     *)
     echo "Usage: /etc/init.d/$NAME {start|stop|restart}" >&2
     exit 3
     ;;
esac
