am force-stop com.byd.launcher
run-as com.byd.launcher sh -c "cat /data/local/tmp/ws.xml > shared_prefs/kachi_workspace.xml; cat /data/local/tmp/lang.xml > shared_prefs/clusternav_lang.xml"
am force-stop com.byd.launcher
