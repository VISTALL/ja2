package javainterpreter.clazz;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javainterpreter.JThread;
import javainterpreter.JavaInterpreter;
import javainterpreter.JavaType;
import javainterpreter.Main;
import javainterpreter.callback.ErrorCallback;
import javainterpreter.callback.VmCallback;
import javainterpreter.member.MethodInfo;

/**
 *
 * @author Attila
 */
public class ClassLoadHelper {

    // TODO same class dereference in multiple threads at same time can cause multiple <clinit>
    public static final Map<String, ClassInfo> classCache = new HashMap<>();
    static boolean testing = false;

    public static void loadClass(String name, JThread thread, VmCallback<ClassInfo> callback, ErrorCallback ec) {
        ClassInfo cached = classCache.get(name);
        if (cached != null) {
            callback.run(cached);
            return;
        }
        if(name.startsWith("[")) {
            callback.run(new ArrayTypeClassInfo(JavaType.getType(name.substring(1))));
            return;
        }
        try {
            loadClassPrivate(name, (result) -> {
                classCache.put(name, result);
                if (!testing)
                    JavaInterpreter.initializeClass(result, thread, () -> callback.run(result), ec);
                else
                    callback.run(result);
            }, ec, thread);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
    private static void loadClassPrivate(String name, VmCallback<ClassInfo> callback, ErrorCallback ec, JThread thread) throws IOException {
        ClassLoadInfo classLoadInfo = BootstrapClassLoader.loadClass(name);
  
        if (classLoadInfo == null) {
            if(Main.getBooleanConfig("vm.load.enableUserClassLoader"))
               loadUserClass(name, callback, ec, thread);
            else JavaInterpreter.error(thread, "java/lang/NoClassDefFoundError", "Class '"+name+"' not found and vm.load.enableUserClassLoader turned off");
            return;
        }
        callback.run(ClassFileParser.parseClass(classLoadInfo));
    }

    private static void loadUserClass(String name, VmCallback<ClassInfo> callback, ErrorCallback ec, JThread thread) {
        // TODO ClassLoader object why constant?
        JavaInterpreter.USER_CLASSLOADER.classInfo
                .getMethod("loadClass", "(Ljava/lang/String;)V", thread, (loadClassMethod) -> {
                    Object[] param = new Object[]{JavaInterpreter.convertString(thread, name)};
                    thread.executeMethod(loadClassMethod, JavaInterpreter.USER_CLASSLOADER, param,
                            (result)
                            -> callback.run((ClassInfo) JavaInterpreter.getClassInfo((JavaObject.JClassInstance) result)));
                }, ec);
    }

    public static ClassInfo instantLoadClass(String name, JThread thread) {
        ClassInfo cached = classCache.get(name);
        if (cached != null)
            return cached;
        try {
            ClassLoadInfo classLoadInfo = BootstrapClassLoader.loadClass(name);
            if(classLoadInfo == null)
                JavaInterpreter.error(thread, "java/lang/NoClassDefFoundError", "Class not found (instantLoadClass): "+name);
            cached = ClassFileParser.parseClass(classLoadInfo);
            classCache.put(name, cached);
            return cached;
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

}
