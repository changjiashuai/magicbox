#include <string.h>  
#include <jni.h>  

jstring Java_online_magicbox_desktop_Jni_hello( JNIEnv* env,
                                                  jobject thiz )  
{  
    return (*env)->NewStringUTF(env, "HelloWorld! I am from JNI !");  
}  
