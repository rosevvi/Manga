package com.manga.integration.wechat;

import com.manga.common.constant.ExceptionMessageConstants;
import com.manga.common.constant.WechatConstants;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;

/**
 * 安全解析微信公众号明文事件推送 XML。
 */
@Component
public class WechatCallbackParser {

    private static final String DISALLOW_DOCTYPE_FEATURE = "http://apache.org/xml/features/disallow-doctype-decl";
    private static final String EXTERNAL_GENERAL_ENTITIES_FEATURE = "http://xml.org/sax/features/external-general-entities";
    private static final String EXTERNAL_PARAMETER_ENTITIES_FEATURE = "http://xml.org/sax/features/external-parameter-entities";

    /**
     * 解析微信事件字段并禁用外部实体和 DTD。
     */
    public WechatCallbackMessage parse(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(DISALLOW_DOCTYPE_FEATURE, true);
            factory.setFeature(EXTERNAL_GENERAL_ENTITIES_FEATURE, false);
            factory.setFeature(EXTERNAL_PARAMETER_ENTITIES_FEATURE, false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            Element root = document.getDocumentElement();
            return new WechatCallbackMessage(
                    text(root, WechatConstants.XML_FROM_USER),
                    text(root, WechatConstants.XML_MESSAGE_TYPE),
                    text(root, WechatConstants.XML_EVENT),
                    text(root, WechatConstants.XML_EVENT_KEY)
            );
        } catch (Exception exception) {
            throw new WechatApiException(ExceptionMessageConstants.WECHAT_CALLBACK_PAYLOAD_INVALID, exception);
        }
    }

    private String text(Element root, String tagName) {
        var nodes = root.getElementsByTagName(tagName);
        return nodes.getLength() == 0 ? null : nodes.item(0).getTextContent();
    }
}
