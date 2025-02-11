package org.jboss.resteasy.plugins.providers.atom;

import org.jboss.resteasy.core.messagebody.AsyncBufferedMessageBodyWriter;
import org.jboss.resteasy.plugins.providers.jaxb.JAXBContextFinder;
import org.jboss.resteasy.plugins.providers.jaxb.JAXBMarshalException;
import org.jboss.resteasy.plugins.providers.jaxb.JAXBUnmarshalException;
import org.jboss.resteasy.plugins.providers.jaxb.hacks.RiHacks;
import org.jboss.resteasy.plugins.providers.resteasy_atom.i18n.Messages;
import org.jboss.resteasy.resteasy_jaxrs.i18n.LogMessages;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.ContextResolver;
import jakarta.ws.rs.ext.MessageBodyReader;
import jakarta.ws.rs.ext.Provider;
import jakarta.ws.rs.ext.Providers;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.HashSet;

/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
@Provider
@Produces("application/atom+*")
@Consumes("application/atom+*")
public class AtomFeedProvider implements MessageBodyReader<Feed>, AsyncBufferedMessageBodyWriter<Feed>
{
   @Context
   protected Providers providers;

   protected JAXBContextFinder getFinder(MediaType type)
   {
      ContextResolver<JAXBContextFinder> resolver = providers.getContextResolver(JAXBContextFinder.class, type);
      if (resolver == null) return null;
      return resolver.getContext(null);
   }

   public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType)
   {
      return Feed.class.isAssignableFrom(type);
   }

   public Feed readFrom(Class<Feed> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, String> httpHeaders, InputStream entityStream) throws IOException, WebApplicationException
   {
      LogMessages.LOGGER.debugf("Provider : %s,  Method : readFrom", getClass().getName());
      JAXBContextFinder finder = getFinder(mediaType);
      if (finder == null)
      {
         throw new JAXBUnmarshalException(Messages.MESSAGES.unableToFindJAXBContext(mediaType));
      }

      try
      {
         JAXBContext ctx = finder.findCachedContext(Feed.class, mediaType, annotations);
         Feed feed = (Feed) ctx.createUnmarshaller().unmarshal(entityStream);
         for (Entry entry : feed.getEntries())
         {
            entry.setFinder(finder);
            if (entry.getContent() != null) entry.getContent().setFinder(finder);
         }
         return feed;
      }
      catch (JAXBException e)
      {
         throw new JAXBUnmarshalException(Messages.MESSAGES.unableToUnmarshal(mediaType), e);
      }
   }

   public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType)
   {
      return Feed.class.isAssignableFrom(type);
   }

   public long getSize(Feed feed, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType)
   {
      return -1;
   }

   public void writeTo(Feed feed, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream) throws IOException, WebApplicationException
   {
      LogMessages.LOGGER.debugf("Provider : %s,  Method : writeTo", getClass().getName());
      JAXBContextFinder finder = getFinder(mediaType);
      if (finder == null)
      {
         throw new JAXBUnmarshalException(Messages.MESSAGES.unableToFindJAXBContext(mediaType));
      }
      HashSet<Class> set = new HashSet<Class>();
      set.add(Feed.class);
      for (Entry entry : feed.getEntries())
      {
         if (entry.getAnyOtherJAXBObject() != null)
         {
            set.add(entry.getAnyOtherJAXBObject().getClass());
         }
         if (entry.getContent() != null && entry.getContent().getJAXBObject() != null)
         {
            set.add(entry.getContent().getJAXBObject().getClass());
         }
      }
      try
      {
         JAXBContext ctx = finder.findCacheContext(mediaType, annotations, set.toArray(new Class[set.size()]));
         Marshaller marshaller = RiHacks.createMarshaller(ctx);
         final Object mapper = RiHacks.createAtomNamespacePrefixMapper();
         marshaller.setProperty("com.sun.xml.bind.namespacePrefixMapper", mapper);

         marshaller.marshal(feed, entityStream);
      }
      catch (JAXBException e)
      {
         throw new JAXBMarshalException(Messages.MESSAGES.unableToMarshal(mediaType), e);
      }
   }
}
