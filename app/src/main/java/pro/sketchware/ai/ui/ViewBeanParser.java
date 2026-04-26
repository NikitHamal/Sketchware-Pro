package pro.sketchware.ai.ui;

import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * ViewBeanParser converts Android XML layout strings into a hierarchy of ViewBeans.
 * Fixed with explicit attribute looping to avoid compiler type conflicts.
 */
public class ViewBeanParser {

    public static List<ViewBean> parse(String xml) throws XmlPullParserException, IOException {
        List<ViewBean> rootViews = new ArrayList<>();
        Stack<ViewBean> beanStack = new Stack<>();
        
        XmlPullParser parser = Xml.newPullParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        parser.setInput(new StringReader(xml));

        int eventType = parser.getEventType();
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                String tagName = parser.getName();
                
                // Explicitly find the android:id attribute to avoid type conflicts
                String idValue = null;
                for (int i = 0; i < parser.getAttributeCount(); i++) {
                    if ("android:id".equals(parser.getAttributeName(i))) {
                        idValue = parser.getAttributeValue(i);
                        break;
                    }
                }

                if (beanStack.isEmpty() && isRootWrapper(tagName)) {
                    ViewBean root = new ViewBean("root", tagName);
                    processAttributes(parser, root);
                    beanStack.push(root);
                    rootViews.add(root);
                } else {
                    String id = (idValue == null || idValue.isEmpty()) ? 
                                "view_" + System.currentTimeMillis() + "_" + rootViews.size() + beanStack.size() : 
                                idValue.replace("@+id/", "");

                    ViewBean bean = new ViewBean(id, tagName);
                    processAttributes(parser, bean);

                    if (!beanStack.isEmpty()) {
                        ViewBean parent = beanStack.peek();
                        bean.parentId = parent.id;
                        parent.children.add(bean);
                    } else {
                        rootViews.add(bean);
                    }
                    beanStack.push(bean);
                }
            } else if (eventType == XmlPullParser.END_TAG) {
                if (!beanStack.isEmpty()) {
                    beanStack.pop();
                }
            }
            eventType = parser.next();
        }
        return rootViews;
    }

    private static void processAttributes(XmlPullParser parser, ViewBean bean) {
        for (int i = 0; i < parser.getAttributeCount(); i++) {
            String attrName = parser.getAttributeName(i);
            String attrValue = parser.getAttributeValue(i);
            bean.setProperty(attrName, attrValue);
        }
    }

    private static boolean isRootWrapper(String tagName) {
        return tagName.equalsIgnoreCase("LinearLayout") || 
               tagName.equalsIgnoreCase("RelativeLayout") || 
               tagName.equalsIgnoreCase("FrameLayout");
    }
}
