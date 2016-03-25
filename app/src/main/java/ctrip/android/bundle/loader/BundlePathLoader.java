package ctrip.android.bundle.loader;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;
import java.util.zip.ZipFile;

import cn.jiajixin.nuwa.util.ReflectionUtils;
import dalvik.system.DexFile;
import dalvik.system.PathClassLoader;

/**
 * Created by yb.wang on 15/4/22.
 */
public class BundlePathLoader {



    static final String TAG = "BundlePathLoader";

    //      private static final String OLD_SECONDARY_FOLDER_NAME = "secondary-dexes";

//        private static final String SECONDARY_FOLDER_NAME = "code_cache" + File.separator +
//                "secondary-dexes";
//
//        private static final int MAX_SUPPORTED_SDK_VERSION = 20;
//
//
//        private static final Set<String> installedApk = new HashSet<String>();

    private BundlePathLoader() {
    }


    public static void installBundleDexs(Context context,ClassLoader loader, File dexDir, List<File> files,String patchClassName,boolean isHotFix)
            throws Exception {
        if (!files.isEmpty()) {
            if (hasLexClassLoader()) {
                Log.d(TAG,"installBundleDexs-YunOs");
                YunOs.install(context,loader, files, dexDir,patchClassName,isHotFix);
            } else if (hasDexClassLoader()) {
                if (Build.VERSION.SDK_INT >= 23) {
                    Log.d(TAG,"installBundleDexs-23+");
                    V23.install(loader, files, dexDir,isHotFix);
                }else if (Build.VERSION.SDK_INT >= 19) {
                    Log.d(TAG,"installBundleDexs-19+");
                    V19.install(loader, files, dexDir,isHotFix);
                } else if (Build.VERSION.SDK_INT >= 14) {
                    Log.d(TAG,"installBundleDexs-14+");
                    V14.install(loader, files, dexDir,isHotFix);
                } else {
                    Log.d(TAG,"installBundleDexs-4+");
                    V4.install(loader, files,isHotFix);
                }
            } else {
                Log.d(TAG,"installBundleDexs-nothing");
            }
        }
    }

//        /**
//         * Returns whether all files in the list are valid zip files.  If {@code files} is empty, then
//         * returns true.
//         */
//        private static boolean checkValidZipFiles(List<File> files) {
//            for (File file : files) {
//                if (!MultiDexExtractor.verifyZipFile(file)) {
//                    return false;
//                }
//            }
//            return true;
//        }

    //this function add by georgeyang 16.3.2,copy from  dodola.hotfixlib.HotFix
    private static final class YunOs {
        private static void install(Context context,ClassLoader loader, List<File> additionalClassPathEntries,
                                    File optimizedDirectory,String patchClassName,boolean isHotFix) throws Exception {
            PathClassLoader obj = (PathClassLoader) loader;
            String patchDexFile  = optimizedDirectory.getAbsolutePath();

            String replaceAll = new File(patchDexFile).getName().replaceAll("\\.[a-zA-Z0-9]+", ".lex");
            Class cls = Class.forName("dalvik.system.LexClassLoader");
            Object newInstance =
                    cls.getConstructor(new Class[] {String.class, String.class, String.class, ClassLoader.class}).newInstance(
                            new Object[] {context.getDir("dex", 0).getAbsolutePath() + File.separator + replaceAll,
                                    context.getDir("dex", 0).getAbsolutePath(), patchDexFile, obj});
            cls.getMethod("loadClass", new Class[] {String.class}).invoke(newInstance, new Object[] {patchClassName});
            setField(obj, PathClassLoader.class, "mPaths",
                    appendArray(getField(obj, PathClassLoader.class, "mPaths"), getField(newInstance, cls, "mRawDexPath")));
            setField(obj, PathClassLoader.class, "mFiles",
                    combineArray(getField(obj, PathClassLoader.class, "mFiles"), getField(newInstance, cls, "mFiles")));
            setField(obj, PathClassLoader.class, "mZips",
                    combineArray(getField(obj, PathClassLoader.class, "mZips"), getField(newInstance, cls, "mZips")));
            setField(obj, PathClassLoader.class, "mLexs",
                    combineArray(getField(obj, PathClassLoader.class, "mLexs"), getField(newInstance, cls, "mDexs")));
        }

    }


    private static Object getField(Object obj, Class cls, String str)
            throws NoSuchFieldException, IllegalAccessException {
        Field declaredField = cls.getDeclaredField(str);
        declaredField.setAccessible(true);
        return declaredField.get(obj);
    }

    private static void setField(Object obj, Class cls, String str, Object obj2)
            throws NoSuchFieldException, IllegalAccessException {
        Field declaredField = cls.getDeclaredField(str);
        declaredField.setAccessible(true);
        declaredField.set(obj, obj2);
    }

    private static Object combineArray(Object obj, Object obj2) {
        Class componentType = obj2.getClass().getComponentType();
        int length = Array.getLength(obj2);
        int length2 = Array.getLength(obj) + length;
        Object newInstance = Array.newInstance(componentType, length2);
        for (int i = 0; i < length2; i++) {
            if (i < length) {
                Array.set(newInstance, i, Array.get(obj2, i));
            } else {
                Array.set(newInstance, i, Array.get(obj, i - length));
            }
        }
        return newInstance;
    }

    private static Object appendArray(Object obj, Object obj2) {
        Class componentType = obj.getClass().getComponentType();
        int length = Array.getLength(obj);
        Object newInstance = Array.newInstance(componentType, length + 1);
        Array.set(newInstance, 0, obj2);
        for (int i = 1; i < length + 1; i++) {
            Array.set(newInstance, i, Array.get(obj, i - 1));
        }
        return newInstance;
    }

    private static boolean hasLexClassLoader() {
        try {
            Class.forName("dalvik.system.LexClassLoader");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static boolean hasDexClassLoader() {
        try {
            Class.forName("dalvik.system.BaseDexClassLoader");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }



    /**
     * Locates a given field anywhere in the class inheritance hierarchy.
     *
     * @param instance an object to search the field into.
     * @param name     field name
     * @return a field object
     * @throws NoSuchFieldException if the field cannot be located
     */
    private static Field findField(Object instance, String name) throws NoSuchFieldException {
        for (Class<?> clazz = instance.getClass(); clazz != null; clazz = clazz.getSuperclass()) {
            try {
                Field field = clazz.getDeclaredField(name);

                if (!field.isAccessible()) {
                    field.setAccessible(true);
                }

                return field;
            } catch (NoSuchFieldException e) {
                // ignore and search next
            }
        }

        throw new NoSuchFieldException("Field " + name + " not found in " + instance.getClass());
    }

    /**
     * Locates a given method anywhere in the class inheritance hierarchy.
     *
     * @param instance       an object to search the method into.
     * @param name           method name
     * @param parameterTypes method parameter types
     * @return a method object
     * @throws NoSuchMethodException if the method cannot be located
     */
    private static Method findMethod(Object instance, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        for (Class<?> clazz = instance.getClass(); clazz != null; clazz = clazz.getSuperclass()) {
            try {

                Method method = clazz.getDeclaredMethod(name, parameterTypes);


                if (!method.isAccessible()) {
                    method.setAccessible(true);
                }

                return method;
            } catch (NoSuchMethodException e) {
                // ignore and search next
            }
        }

        throw new NoSuchMethodException("Method " + name + " with parameters " +
                Arrays.asList(parameterTypes) + " not found in " + instance.getClass());
    }

    /**
     * Replace the value of a field containing a non null array, by a new array containing the
     * elements of the original array plus the elements of extraElements.
     *
     * @param instance      the instance whose field is to be modified.
     * @param fieldName     the field to modify.
     * @param extraElements elements to append at the end of the array.
     */
    private static void expandFieldArray(Object instance, String fieldName,
                                         Object[] extraElements,boolean isHotFix) throws NoSuchFieldException, IllegalArgumentException,
            IllegalAccessException {
        synchronized (BundlePathLoader.class) {
            Field jlrField = findField(instance, fieldName);


            Object[] original = (Object[]) jlrField.get(instance);
            Object[] combined = (Object[]) Array.newInstance(
                    original.getClass().getComponentType(), original.length + extraElements.length);
            if(isHotFix) {
                System.arraycopy(extraElements, 0, combined, 0, extraElements.length);
                System.arraycopy(original, 0, combined, extraElements.length, original.length);
            }else {
                System.arraycopy(original, 0, combined, 0, original.length);
                System.arraycopy(extraElements, 0, combined, original.length, extraElements.length);
            }


            Log.i("nuwa","檢查順序!!");
            int allLength = Array.getLength(combined);
            for (int k = 0; k < allLength; ++k) {
                Object element = Array.get(combined,k);
                Log.i("nuwa","element:" + element);
                // load error:No field file in class Ldalvik/system/DexPathList$Element; (declaration of 'dalvik.system.DexPathList$Element' appears in /system/framework/core-libart.jar)
                Object dexFile = ReflectionUtils.getField(element, element.getClass(), "file");
                Log.i("nuwa","dexFile:" + dexFile.toString());
            }

            jlrField.set(instance, combined);
        }
    }


    private static final class V23 {

        private static void install(ClassLoader loader, List<File> additionalClassPathEntries,
                                    File optimizedDirectory,boolean isHotFix)
                throws IllegalArgumentException, IllegalAccessException,
                NoSuchFieldException, InvocationTargetException, NoSuchMethodException, InstantiationException {

            Field pathListField = findField(loader, "pathList");
            Object dexPathList = pathListField.get(loader);
            Field dexElement = findField(dexPathList, "dexElements");
            Class<?> elementType = dexElement.getType().getComponentType();
            Method loadDex = findMethod(dexPathList, "loadDexFile", File.class, File.class);
            Object dex = loadDex.invoke(dexPathList, additionalClassPathEntries.get(0), optimizedDirectory);
            Constructor<?> constructor = elementType.getConstructor(File.class, boolean.class, File.class, DexFile.class);
            Object element = constructor.newInstance(new File(""), false, additionalClassPathEntries.get(0), dex);
            Object[] newEles=new Object[1];
            newEles[0]=element;
            expandFieldArray(dexPathList, "dexElements",newEles,isHotFix);
        }

    }

    /**
     * Installer for platform versions 19.
     */
    private static final class V19 {

        private static void install(ClassLoader loader, List<File> additionalClassPathEntries,
                                    File optimizedDirectory,boolean isHotFix)
                throws IllegalArgumentException, IllegalAccessException,
                NoSuchFieldException, InvocationTargetException, NoSuchMethodException, IOException {
            /* The patched class loader is expected to be a descendant of
             * dalvik.system.BaseDexClassLoader. We modify its
             * dalvik.system.DexPathList pathList field to append additional DEX
             * file entries.
             */
            Field pathListField = findField(loader, "pathList");
            Object dexPathList = pathListField.get(loader);
            ArrayList<IOException> suppressedExceptions = new ArrayList<IOException>();
            expandFieldArray(dexPathList, "dexElements", makeDexElements(dexPathList,
                    new ArrayList<File>(additionalClassPathEntries), optimizedDirectory,
                    suppressedExceptions),isHotFix);
            if (suppressedExceptions.size() > 0) {
                for (IOException e : suppressedExceptions) {
                    Log.w(TAG, "Exception in makeDexElement", e);

                }
                throw suppressedExceptions.get(0);
            }
        }

        /**
         * A wrapper around
         * {@code private static final dalvik.system.DexPathList#makeDexElements}.
         */
        private static Object[] makeDexElements(
                Object dexPathList, ArrayList<File> files, File optimizedDirectory,
                ArrayList<IOException> suppressedExceptions)
                throws IllegalAccessException, InvocationTargetException,
                NoSuchMethodException {
            Method makeDexElements =
                    findMethod(dexPathList, "makeDexElements", ArrayList.class, File.class,
                            ArrayList.class);

            return (Object[]) makeDexElements.invoke(dexPathList, files, optimizedDirectory,
                    suppressedExceptions);
        }
    }

    /**
     * Installer for platform versions 14, 15, 16, 17 and 18.
     */
    private static final class V14 {

        private static void install(ClassLoader loader, List<File> additionalClassPathEntries,
                                    File optimizedDirectory,boolean isHotFix)
                throws IllegalArgumentException, IllegalAccessException,
                NoSuchFieldException, InvocationTargetException, NoSuchMethodException {
            /* The patched class loader is expected to be a descendant of
             * dalvik.system.BaseDexClassLoader. We modify its
             * dalvik.system.DexPathList pathList field to append additional DEX
             * file entries.
             */
            Field pathListField = findField(loader, "pathList");
            Object dexPathList = pathListField.get(loader);
            expandFieldArray(dexPathList, "dexElements", makeDexElements(dexPathList,
                    new ArrayList<File>(additionalClassPathEntries), optimizedDirectory),isHotFix);
        }

        /**
         * A wrapper around
         * {@code private static final dalvik.system.DexPathList#makeDexElements}.
         */
        private static Object[] makeDexElements(
                Object dexPathList, ArrayList<File> files, File optimizedDirectory)
                throws IllegalAccessException, InvocationTargetException,
                NoSuchMethodException {
            Method makeDexElements =
                    findMethod(dexPathList, "makeDexElements", ArrayList.class, File.class);

            return (Object[]) makeDexElements.invoke(dexPathList, files, optimizedDirectory);
        }
    }

    /**
     * Installer for platform versions 4 to 13.
     */
    private static final class V4 {
        private static void install(ClassLoader loader, List<File> additionalClassPathEntries,boolean isHotFix)
                throws IllegalArgumentException, IllegalAccessException,
                NoSuchFieldException, IOException {
            /* The patched class loader is expected to be a descendant of
             * dalvik.system.DexClassLoader. We modify its
             * fields mPaths, mFiles, mZips and mDexs to append additional DEX
             * file entries.
             */
            int extraSize = additionalClassPathEntries.size();

            Field pathField = findField(loader, "path");

            StringBuilder path = new StringBuilder((String) pathField.get(loader));
            String[] extraPaths = new String[extraSize];
            File[] extraFiles = new File[extraSize];
            ZipFile[] extraZips = new ZipFile[extraSize];
            DexFile[] extraDexs = new DexFile[extraSize];
            for (ListIterator<File> iterator = additionalClassPathEntries.listIterator();
                 iterator.hasNext(); ) {
                File additionalEntry = iterator.next();
                String entryPath = additionalEntry.getAbsolutePath();
                path.append(':').append(entryPath);
                int index = iterator.previousIndex();
                extraPaths[index] = entryPath;
                extraFiles[index] = additionalEntry;
                extraZips[index] = new ZipFile(additionalEntry);
                extraDexs[index] = DexFile.loadDex(entryPath, entryPath + ".dex", 0);
            }

            pathField.set(loader, path.toString());
            expandFieldArray(loader, "mPaths", extraPaths,isHotFix);
            expandFieldArray(loader, "mFiles", extraFiles,isHotFix);
            expandFieldArray(loader, "mZips", extraZips,isHotFix);
            expandFieldArray(loader, "mDexs", extraDexs,isHotFix);
        }
    }


}