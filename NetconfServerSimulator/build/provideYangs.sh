echo "Provide yangs to ODL tool"
YANG_HOME="../yang/openYuma"
if [ -z "$ODL_KARAF_HOME"  ] ; then

  echo "Need ODL_KARAF_HOME spezified"

else 
  if [ -d "$YANG_HOME" ] ; then
    YANG_DEST="$ODL_KARAF_HOME/cache/schema"

    echo "Provide yangs:"
    echo "   From: $YANG_HOME"
    echo "   To  : $YANG_DEST"
    cp "$YANG_HOME"/* $YANG_DEST

  else 

    echo "Can not locate $YANG_HOME. Execute in Simulator-Home / build."

  fi
fi
