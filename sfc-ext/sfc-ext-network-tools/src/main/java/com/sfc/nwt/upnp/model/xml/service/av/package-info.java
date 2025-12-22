/**
 * UPnP服务模型包
 * 包含用于反序列化SCPD（Service Control Protocol Definition）XML文档的实体类
 *
 * @see com.sfc.nwt.upnp.model.xml.service.av.Scpd
 */
@XmlSchema(
        namespace = "urn:schemas-upnp-org:service-1-0",
        elementFormDefault = XmlNsForm.QUALIFIED
)
package com.sfc.nwt.upnp.model.xml.service.av;

import jakarta.xml.bind.annotation.XmlNsForm;
import jakarta.xml.bind.annotation.XmlSchema;