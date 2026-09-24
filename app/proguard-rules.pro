# MyGub — правила R8 для релизной сборки.

# Все классы переезжают в один безымянный пакет, имена становятся a, b, c…
-repackageclasses ''
-allowaccessmodification
-overloadaggressively

# Номера строк оставляем (для отчётов об ошибках), имя исходника прячем
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute MyGub

# Убираем отладочный вывод из релиза
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# OkHttp / Okio: необязательные зависимости, которых нет в приложении
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn okhttp3.internal.platform.**
