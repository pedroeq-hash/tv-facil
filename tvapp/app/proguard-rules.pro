# A libVLC chama classes Java pelo codigo nativo (JNI) e o AAR nao traz regras proprias:
# sem isto, o R8 apaga/renomeia essas classes e o app fecha ao tocar.
-keep class org.videolan.** { *; }
