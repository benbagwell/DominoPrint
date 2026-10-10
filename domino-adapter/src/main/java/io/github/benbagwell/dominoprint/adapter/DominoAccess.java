package io.github.benbagwell.dominoprint.adapter;

import java.lang.reflect.*;
import java.util.*;

/** Runtime dispatch keeps proprietary Domino classes out of the offline classpath. */
final class DominoAccess {
    private DominoAccess(){}
    static Object call(Object target,String name,Object... arguments){
        Objects.requireNonNull(target);
        for(var method:target.getClass().getMethods())if(method.getName().equals(name)&&matches(method.getParameterTypes(),arguments)){
            try{return method.invoke(target,arguments);}
            catch(InvocationTargetException ex){var cause=ex.getCause();throw new DominoImportException("Domino "+name+" failed: "+cause.getMessage(),cause);}
            catch(ReflectiveOperationException ex){throw new DominoImportException("Cannot access Domino method "+name,ex);}
        }
        throw new DominoImportException("Required Domino method not found: "+name);
    }
    private static boolean matches(Class<?>[] types,Object[] args){if(types.length!=args.length)return false;for(int i=0;i<types.length;i++){Class<?> type=types[i];if(type.isPrimitive())type=type==boolean.class?Boolean.class:type==int.class?Integer.class:type==double.class?Double.class:type==long.class?Long.class:type;if(args[i]!=null&&!type.isInstance(args[i]))return false;}return true;}
    static String string(Object target,String method){return Objects.toString(call(target,method),"");}
    static void recycle(Object object){if(object!=null)call(object,"recycle");}
}
