//$Id: GTransformer.java 7814 2008-10-31 13:23:21Z gertsp $
/*
 * <p><b>License and Copyright: </b>The contents of this file is subject to the
 * same open source license as the Fedora Repository System at www.fedora-commons.org
 * Copyright &copy; 2006, 2007, 2008 by The Technical University of Denmark.
 * All rights reserved.</p>
 */
package dk.defxws.fedoragsearch.server;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Date;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.xalan.processor.TransformerFactoryImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dk.defxws.fedoragsearch.server.errors.ConfigException;
import dk.defxws.fedoragsearch.server.errors.GenericSearchException;
import dk.defxws.fedoragsearch.server.utils.Stream;

/**
 * performs the stylesheet transformations
 * 
 * @author  gsp@dtv.dk
 * @version 
 */
public class GTransformer {
    
    private static final Logger logger =
        LoggerFactory.getLogger(GTransformer.class);
    
    public GTransformer() {
    }
    
    /**
     * 
     *
     * @throws TransformerConfigurationException, TransformerException.
     */
    public Transformer getTransformer(String xsltPath) 
    throws ConfigException {
        return getTransformer(xsltPath, null);
    }
    
    public Transformer getTransformer(String xsltPath, URIResolver uriResolver) 
    throws ConfigException {
        Transformer transformer = null;
        String relativeXsltPath = xsltPath;
        if (!relativeXsltPath.startsWith("/")) {
        	relativeXsltPath = "/" + relativeXsltPath;
        }
        try {
            InputStream stylesheet = null;
            //MIH: if xsltPath starts with http, get stylesheet from url
            if (xsltPath.startsWith("http")) {
                stylesheet = Config.getCurrentConfig().getResourceFromUrl(xsltPath);
            } else {
                stylesheet = Config.getCurrentConfig().getResourceInputStream(relativeXsltPath + ".xslt");
            }
            if (stylesheet==null) {
                throw new ConfigException(relativeXsltPath+" not found");
            }
            //MIH: Explicitly use Xalan Transformer Factory ////////////////////
        	TransformerFactoryImpl tfactory = new TransformerFactoryImpl();
            ///////////////////////////////////////////////////////////////////
            //MIH: set URIResolver to TransformerFactory and not to Transformer
            if (uriResolver!=null) {
                tfactory.setURIResolver(uriResolver);
            }

            StreamSource xslt = new StreamSource(stylesheet);
            transformer = tfactory.newTransformer(xslt);
        } catch (Exception e) {
            logger.info("No xslt stylesheet found!");
        }
        return transformer;
    }
    
    /**
     * 
     *
     * @throws TransformerConfigurationException, TransformerException.
     */
    public void transform(String xsltName, StreamSource sourceStream, StreamResult destStream)
    throws GenericSearchException {
        Transformer transformer = getTransformer(xsltName);
        try {
            transformer.transform(sourceStream, destStream);
        } catch (TransformerException e) {
            throw new GenericSearchException("transform "+xsltName+".xslt:\n", e);
        }
    }

    public Stream transform(String xsltName, Source sourceStream, Object[] params)
    throws GenericSearchException {
        return transform (xsltName, sourceStream, null, params);
    }

    public Stream transform(String xsltName, Source sourceStream, URIResolver uriResolver, Object[] params)
    throws GenericSearchException {
        Stream stream = new Stream();
        if (logger.isDebugEnabled())
            logger.debug("xsltName="+xsltName);
        Transformer transformer = getTransformer(xsltName, uriResolver);
        if(transformer != null) {
            for(int i = 0; i < params.length; i = i + 2) {
                Object value = params[i + 1];
                if(value == null) {
                    value = "";
                }
                transformer.setParameter((String) params[i], value);
            }
            transformer.setParameter("DATETIME", new Date());
            StreamResult destStream = new StreamResult(stream);
            try {
                transformer.transform(sourceStream, destStream);
                stream.lock();
            } catch(TransformerException e) {
                throw new GenericSearchException("transform " + xsltName + ".xslt:\n", e);
            } catch(IOException e) {
                throw new GenericSearchException(e.getMessage(), e);
            }
            //      if (logger.isDebugEnabled())
            //      logger.debug("sw="+sw.getBuffer().toString());
        } else {
            try {
                XMLInputFactory factory = XMLInputFactory.newInstance();
                XMLEventReader reader = factory.createXMLEventReader(sourceStream);
                while(reader.hasNext()) {
                    XMLEvent event = reader.nextEvent();
                    if(event.getEventType() == XMLEvent.CHARACTERS) {
                        stream.write(event.asCharacters().getData().getBytes("UTF-8"));
                    }
                }
            } catch(IOException e) {
                throw new GenericSearchException(e.getMessage(), e);
            } catch(XMLStreamException e) {
                throw new GenericSearchException(e.getMessage(), e);
            }
        }
        return stream;
    }


    
    /**
     * 
     *
     * @throws TransformerConfigurationException, TransformerException.
     */
    public void transformToFile(String xsltName, StreamSource sourceStream, Object[] params, String filePath) 
    throws GenericSearchException {
        if (logger.isDebugEnabled())
            logger.debug("xsltName="+xsltName);
        Transformer transformer = getTransformer(xsltName);
        for (int i=0; i<params.length; i=i+2) {
            Object value = params[i+1];
            if (value==null) value = "";
            transformer.setParameter((String)params[i], value);
        }
        transformer.setParameter("DATETIME", new Date());
        StreamResult destStream = new StreamResult(new File(filePath));
        try {
            transformer.transform(sourceStream, destStream);
        } catch (TransformerException e) {
            throw new GenericSearchException("transform "+xsltName+".xslt:\n", e);
        }
    }
    
    /**
     * 
     *
     * @throws TransformerConfigurationException, TransformerException.
     */
    public Stream transform(String xsltName, StreamSource sourceStream)
    throws GenericSearchException {
        return transform(xsltName, sourceStream, new String[]{});
    }
    
    /**
     * 
     *
     * @throws TransformerConfigurationException, TransformerException.
     */
    public Stream transform(String xsltName, StringBuffer sb, String[] params)
    throws GenericSearchException {
//      if (logger.isDebugEnabled())
//      logger.debug("sb="+sb);
        StringReader sr = new StringReader(sb.toString());
        Stream result = transform(xsltName, new StreamSource(sr), params);
//      if (logger.isDebugEnabled())
//      logger.debug("xsltName="+xsltName+" result="+result);
        return result;
    }
    
    /**
     * 
     *
     * @throws TransformerConfigurationException, TransformerException.
     */
    public Stream transform(String xsltName, StringBuffer sb)
    throws GenericSearchException {
        return transform(xsltName, sb, new String[]{});
    }
    
    public static void main(String[] args) {
        int argCount=2;
        try {
            if (args.length==argCount) {
                File f = new File(args[1]);
                StreamSource ss = new StreamSource(new File(args[1]));
                GTransformer gt = new GTransformer();
                StreamResult destStream = new StreamResult(new StringWriter());
                gt.transform(args[0], ss, destStream);
                StringWriter sw = (StringWriter)destStream.getWriter();
                System.out.print(sw.getBuffer().toString());
            } else {
                throw new IOException("Must supply " + argCount + " arguments.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println(
            "Usage: GTransformer xsltName xmlFileName");
        }
    }
    
//  /**
//   * This is an unfinished attempt at caching entities for efficiency.
//   *
//   * @throws TransformerConfigurationException, TransformerException.
//   */
//  public StringBuffer transform(String xsltName, StreamSource sourceStream, Object[] params) 
//  throws GenericSearchException {
//      if (logger.isDebugEnabled())
//          logger.debug("xsltName="+xsltName);
//      Transformer transformer = getTransformer(xsltName);
//      for (int i=0; i<params.length; i=i+2) {
//          Object value = params[i+1];
//          if (value==null) value = "";
//          transformer.setParameter((String)params[i], value);
//      }
//      transformer.setParameter("DATETIME", new Date());
//      StreamResult destStream = new StreamResult(new StringWriter());  
//
//      InputSource src = new InputSource(sourceStream.getInputStream());
//      src.setSystemId(sourceStream.getSystemId());
//
//      XMLReader rdr = null;
//		try {
//			rdr = XMLReaderFactory.createXMLReader(javax.xml.parsers.SAXParserFactory.newInstance().newSAXParser().getXMLReader().getClass().getName());
//		} catch (SAXException e) {
//			throw new GenericSearchException("transform "+xsltName+".xslt:\n", e);
//		} catch (ParserConfigurationException e) {
//			throw new GenericSearchException("transform "+xsltName+".xslt:\n", e);
//		}
////		FIX ME, this will not reuse earlier cached entities
//      rdr.setEntityResolver(new CachedEntityResolver());
//
//      Source s = new SAXSource(rdr, src);
//      
//      try {
//          transformer.transform(s, destStream);
//      } catch (TransformerException e) {
//          throw new GenericSearchException("transform "+xsltName+".xslt:\n", e);
//      }
//      StringWriter sw = (StringWriter)destStream.getWriter();
////    if (logger.isDebugEnabled())
////    logger.debug("sw="+sw.getBuffer().toString());
//      return sw.getBuffer();
//  }
//    
//    private static class CachedEntityResolver implements EntityResolver {
//        private final Map cache = new HashMap();
//
//        public InputSource resolveEntity(String publicId, String systemId) throws IOException {
//          byte[] res = (byte[]) cache.get(systemId);
//          if (res == null) {
//            res = IOUtils.toByteArray(new URL(systemId).openStream());
//            cache.put(systemId, res);
//          }
//
//          InputSource is = new InputSource(new ByteArrayInputStream(res));
//          is.setPublicId(publicId);
//          is.setSystemId(systemId);
//
//          return is;
//        }
//      }
    
}
