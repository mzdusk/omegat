/**************************************************************************
 OmegaT - Computer Assisted Translation (CAT) tool
          with fuzzy matching, translation memory, keyword search,
          glossaries, and translation leveraging into updated projects.

 Copyright (C) 2014 Alex Buloichik, Didier Briel
               Home page: http://www.omegat.org/
               Support center: http://groups.yahoo.com/group/OmegaT/

 This file is part of OmegaT.

 OmegaT is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 OmegaT is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 **************************************************************************/

package org.omegat.gui.glossary.taas;

import gen.taas.TaasArrayOfTerm;
import gen.taas.TaasCollection;
import gen.taas.TaasCollections;
import gen.taas.TaasExtractionResult;
import gen.taas.TaasTerm;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.List;

import javax.xml.bind.JAXBContext;

import org.omegat.util.Base64;
import org.omegat.util.LFileCopy;
import org.omegat.util.Language;
import org.omegat.util.Log;

/**
 * Client for TaaS REST service.
 * 
 * @author Alex Buloichik (alex73mail@gmail.com)
 */
public class TaaSClient {
    static final Charset UTF8 = Charset.forName("UTF-8");

    public static final String WS_URL = "https://api.taas-project.eu";
    /** Machine user name */
    public static final String M_USERNAME = "OmegaT";
    /** Machine password */
    public static final String M_PASSWORD = "Ts1DW4^UpE";

    /**
     * 1-statistical terminology annotation, 2- statistical terminology annotation with references to
     * terminology entries, 4- Terminology DB based terminology annotation (fast)
     */
    public static final int EXTRACTION_METHOD = 4;

    private final JAXBContext context;

    private final String basicAuth;
    private final String taasUserKey;

    public TaaSClient() throws Exception {
        this.basicAuth = "Basic "
                + Base64.encodeBytes((M_USERNAME + ":" + M_PASSWORD).getBytes("ISO-8859-1"));
        this.taasUserKey = System.getProperty("taas.user.key");
        if (this.taasUserKey == null) {
            Log.logWarningRB("TAAS_API_KEY_NOT_FOUND");
        }
        context = JAXBContext.newInstance(TaasCollections.class, TaasArrayOfTerm.class,
                TaasExtractionResult.class);
    }

    /**
     * Request specified URL and check response code.
     */
    HttpURLConnection requestGet(String url) throws IOException, Unauthorized, FormatError {
        Log.logInfoRB("TAAS_REQUEST", url);
        HttpURLConnection conn;
        conn = (HttpURLConnection) new URL(url).openConnection();

        conn.setRequestProperty("Authorization", basicAuth);
        if (taasUserKey != null) {
            conn.setRequestProperty("TaaS-User-Key", taasUserKey);
        }
        conn.setRequestProperty("Accept", "text/xml");

        if (conn.getResponseCode() == HttpURLConnection.HTTP_UNAUTHORIZED) {
            throw new Unauthorized();
        }

        if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
            throw new FormatError(conn.getResponseCode() + " " + conn.getResponseMessage());
        }
        return conn;
    }

    /**
     * Request specified URL and check response code.
     */
    HttpURLConnection requestPost(String url, String body) throws IOException, Unauthorized, FormatError {
        Log.logInfoRB("TAAS_REQUEST", url);
        HttpURLConnection conn;
        conn = (HttpURLConnection) new URL(url).openConnection();

        conn.setRequestProperty("Authorization", basicAuth);
        if (taasUserKey != null) {
            conn.setRequestProperty("TaaS-User-Key", taasUserKey);
        }
        conn.setRequestProperty("Accept", "text/xml");
        conn.setRequestMethod("POST");

        conn.setRequestProperty("Content-Type", "text/plain");// ; charset=UTF-8
        conn.setDoOutput(true);
        OutputStream out = conn.getOutputStream();
        try {
            out.write(body.getBytes(UTF8));
        } finally {
            out.close();
        }

        if (conn.getResponseCode() == HttpURLConnection.HTTP_UNAUTHORIZED) {
            throw new Unauthorized();
        }

        if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
            throw new FormatError(conn.getResponseCode() + " " + conn.getResponseMessage());
        }
        return conn;
    }

    /**
     * Check content type of response.
     */
    void checkXMLContentType(HttpURLConnection conn) throws FormatError {
        String contentType = conn.getHeaderField("Content-Type");
        if (contentType == null) {
            throw new FormatError("Empty Content-Type header");
        }
        String ct = contentType.replace(" ", "").toLowerCase();
        if (!"text/xml".equals(ct) && !"application/xml".equals(ct)) {
            throw new FormatError("Wrong Content-Type header: " + contentType);
        }
    }

    /**
     * Check content type of response.
     */
    void checkXMLUTF8ContentType(HttpURLConnection conn) throws FormatError {
        String contentType = conn.getHeaderField("Content-Type");
        if (contentType == null) {
            throw new FormatError("Empty Content-Type header");
        }
        String ct = contentType.replace(" ", "").toLowerCase();
        if (!"text/xml;charset=utf-8".equals(ct) && !"application/xml;charset=utf-8".equals(ct)) {
            throw new FormatError("Wrong Content-Type header: " + contentType);
        }
    }

    /**
     * Read content as UTF-8 string.
     */
    String readUTF8(HttpURLConnection conn) throws IOException {
        InputStream in = conn.getInputStream();
        try {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            LFileCopy.copy(in, o);
            return new String(o.toByteArray(), UTF8);
        } finally {
            in.close();
        }
    }

    /**
     * Get a List of Collections.
     */
    List<TaasCollection> getCollectionsList() throws IOException, Unauthorized, FormatError {
        HttpURLConnection conn = requestGet(WS_URL + "/collections");
        checkXMLUTF8ContentType(conn);

        String data = readUTF8(conn);
        TaasCollections result;
        try {
            result = (TaasCollections) context.createUnmarshaller().unmarshal(new StringReader(data));
        } catch (Exception ex) {
            throw new FormatError("Wrong content: " + ex.getMessage());
        }
        return result.getCollection();
    }

    /**
     * Download specific collection into file.
     */
    void downloadCollection(long collectionId, File outFile) throws Exception {
        HttpURLConnection conn = requestGet(WS_URL + "/collections/" + collectionId);
        checkXMLContentType(conn);

        InputStream in = conn.getInputStream();
        try {
            OutputStream out = new BufferedOutputStream(new FileOutputStream(outFile));
            try {
                TaaSPlugin.filterTaasResult(in, out);
            } finally {
                out.close();
            }
        } finally {
            in.close();
        }
    }

    /**
     * Look ups translation for given term.
     */
    List<TaasTerm> termLookup(Language sourceLang, Language targetLang, String term) throws IOException,
            Unauthorized, FormatError {
        HttpURLConnection conn = requestGet(WS_URL + "/lookup/" + sourceLang.getLanguageCode() + "/"
                + URLEncoder.encode(term, "UTF-8") + "?targetLang=" + targetLang.getLanguageCode());
        checkXMLUTF8ContentType(conn);

        String data = readUTF8(conn);
        TaasArrayOfTerm result;
        try {
            result = (TaasArrayOfTerm) context.createUnmarshaller().unmarshal(new StringReader(data));
        } catch (Exception ex) {
            throw new FormatError("Wrong content: " + ex.getMessage());
        }
        return result.getTerm();
    }

    TaasExtractionResult termExtraction(Language sourceLang, Language targetLang, String text)
            throws IOException, Unauthorized, FormatError {
        HttpURLConnection conn = requestPost(
                WS_URL + "/extraction/?sourceLang=" + sourceLang.getLanguageCode() + "&targetLang="
                        + targetLang.getLanguageCode() + "&method=" + EXTRACTION_METHOD, text);
        checkXMLUTF8ContentType(conn);
        String data = readUTF8(conn);
        TaasExtractionResult result;
        try {
            result = (TaasExtractionResult) context.createUnmarshaller().unmarshal(new StringReader(data));
        } catch (Exception ex) {
            throw new FormatError("Wrong content: " + ex.getMessage());
        }
        return result;
    }

    public static class Unauthorized extends Exception {
    }

    public static class FormatError extends Exception {
        public FormatError(String text) {
            super(text);
        }
    }
}
