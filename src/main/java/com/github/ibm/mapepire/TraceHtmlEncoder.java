package com.github.ibm.mapepire;

import java.util.HashMap;

// Derived from https://howtodoinjava.com/java/string/escape-html-encode-string/
// Presumed to be proper and valid fair use
class TraceHtmlEncoder {

    private static final HashMap<Character, String> s_htmlEncodeChars = new HashMap<>();

    static {
        // Special characters for HTML
        s_htmlEncodeChars.put('\u0026', "&amp;");
        s_htmlEncodeChars.put('\u003C', "&lt;");
        s_htmlEncodeChars.put('\u003E', "&gt;");
        s_htmlEncodeChars.put('\u0022', "&quot;");
        s_htmlEncodeChars.put('\u0152', "&OElig;");
        s_htmlEncodeChars.put('\u0153', "&oelig;");
        s_htmlEncodeChars.put('\u0160', "&Scaron;");
        s_htmlEncodeChars.put('\u0161', "&scaron;");
        s_htmlEncodeChars.put('\u0178', "&Yuml;");
        s_htmlEncodeChars.put('\u02C6', "&circ;");
        s_htmlEncodeChars.put('\u02DC', "&tilde;");
        s_htmlEncodeChars.put('\u2002', "&ensp;");
        s_htmlEncodeChars.put('\u2003', "&emsp;");
        s_htmlEncodeChars.put('\u2009', "&thinsp;");
        s_htmlEncodeChars.put('\u200C', "&zwnj;");
        s_htmlEncodeChars.put('\u200D', "&zwj;");
        s_htmlEncodeChars.put('\u200E', "&lrm;");
        s_htmlEncodeChars.put('\u200F', "&rlm;");
        s_htmlEncodeChars.put('\u2013', "&ndash;");
        s_htmlEncodeChars.put('\u2014', "&mdash;");
        s_htmlEncodeChars.put('\u2018', "&lsquo;");
        s_htmlEncodeChars.put('\u2019', "&rsquo;");
        s_htmlEncodeChars.put('\u201A', "&sbquo;");
        s_htmlEncodeChars.put('\u201C', "&ldquo;");
        s_htmlEncodeChars.put('\u201D', "&rdquo;");
        s_htmlEncodeChars.put('\u201E', "&bdquo;");
        s_htmlEncodeChars.put('\u2020', "&dagger;");
        s_htmlEncodeChars.put('\u2021', "&Dagger;");
        s_htmlEncodeChars.put('\u2030', "&permil;");
        s_htmlEncodeChars.put('\u2039', "&lsaquo;");
        s_htmlEncodeChars.put('\u203A', "&rsaquo;");
        s_htmlEncodeChars.put('\u20AC', "&euro;");

        // Character entity references for ISO 8859-1 characters
        s_htmlEncodeChars.put('\u00A0', "&nbsp;");
        s_htmlEncodeChars.put('\u00A1', "&iexcl;");
        s_htmlEncodeChars.put('\u00A2', "&cent;");
        s_htmlEncodeChars.put('\u00A3', "&pound;");
        s_htmlEncodeChars.put('\u00A4', "&curren;");
        s_htmlEncodeChars.put('\u00A5', "&yen;");
        s_htmlEncodeChars.put('\u00A6', "&brvbar;");
        s_htmlEncodeChars.put('\u00A7', "&sect;");
        s_htmlEncodeChars.put('\u00A8', "&uml;");
        s_htmlEncodeChars.put('\u00A9', "&copy;");
        s_htmlEncodeChars.put('\u00AA', "&ordf;");
        s_htmlEncodeChars.put('\u00AB', "&laquo;");
        s_htmlEncodeChars.put('\u00AC', "&not;");
        s_htmlEncodeChars.put('\u00AD', "&shy;");
        s_htmlEncodeChars.put('\u00AE', "&reg;");
        s_htmlEncodeChars.put('\u00AF', "&macr;");
        s_htmlEncodeChars.put('\u00B0', "&deg;");
        s_htmlEncodeChars.put('\u00B1', "&plusmn;");
        s_htmlEncodeChars.put('\u00B2', "&sup2;");
        s_htmlEncodeChars.put('\u00B3', "&sup3;");
        s_htmlEncodeChars.put('\u00B4', "&acute;");
        s_htmlEncodeChars.put('\u00B5', "&micro;");
        s_htmlEncodeChars.put('\u00B6', "&para;");
        s_htmlEncodeChars.put('\u00B7', "&middot;");
        s_htmlEncodeChars.put('\u00B8', "&cedil;");
        s_htmlEncodeChars.put('\u00B9', "&sup1;");
        s_htmlEncodeChars.put('\u00BA', "&ordm;");
        s_htmlEncodeChars.put('\u00BB', "&raquo;");
        s_htmlEncodeChars.put('\u00BC', "&frac14;");
        s_htmlEncodeChars.put('\u00BD', "&frac12;");
        s_htmlEncodeChars.put('\u00BE', "&frac34;");
        s_htmlEncodeChars.put('\u00BF', "&iquest;");
        s_htmlEncodeChars.put('\u00C0', "&Agrave;");
        s_htmlEncodeChars.put('\u00C1', "&Aacute;");
        s_htmlEncodeChars.put('\u00C2', "&Acirc;");
        s_htmlEncodeChars.put('\u00C3', "&Atilde;");
        s_htmlEncodeChars.put('\u00C4', "&Auml;");
        s_htmlEncodeChars.put('\u00C5', "&Aring;");
        s_htmlEncodeChars.put('\u00C6', "&AElig;");
        s_htmlEncodeChars.put('\u00C7', "&Ccedil;");
        s_htmlEncodeChars.put('\u00C8', "&Egrave;");
        s_htmlEncodeChars.put('\u00C9', "&Eacute;");
        s_htmlEncodeChars.put('\u00CA', "&Ecirc;");
        s_htmlEncodeChars.put('\u00CB', "&Euml;");
        s_htmlEncodeChars.put('\u00CC', "&Igrave;");
        s_htmlEncodeChars.put('\u00CD', "&Iacute;");
        s_htmlEncodeChars.put('\u00CE', "&Icirc;");
        s_htmlEncodeChars.put('\u00CF', "&Iuml;");
        s_htmlEncodeChars.put('\u00D0', "&ETH;");
        s_htmlEncodeChars.put('\u00D1', "&Ntilde;");
        s_htmlEncodeChars.put('\u00D2', "&Ograve;");
        s_htmlEncodeChars.put('\u00D3', "&Oacute;");
        s_htmlEncodeChars.put('\u00D4', "&Ocirc;");
        s_htmlEncodeChars.put('\u00D5', "&Otilde;");
        s_htmlEncodeChars.put('\u00D6', "&Ouml;");
        s_htmlEncodeChars.put('\u00D7', "&times;");
        s_htmlEncodeChars.put('\u00D8', "&Oslash;");
        s_htmlEncodeChars.put('\u00D9', "&Ugrave;");
        s_htmlEncodeChars.put('\u00DA', "&Uacute;");
        s_htmlEncodeChars.put('\u00DB', "&Ucirc;");
        s_htmlEncodeChars.put('\u00DC', "&Uuml;");
        s_htmlEncodeChars.put('\u00DD', "&Yacute;");
        s_htmlEncodeChars.put('\u00DE', "&THORN;");
        s_htmlEncodeChars.put('\u00DF', "&szlig;");
        s_htmlEncodeChars.put('\u00E0', "&agrave;");
        s_htmlEncodeChars.put('\u00E1', "&aacute;");
        s_htmlEncodeChars.put('\u00E2', "&acirc;");
        s_htmlEncodeChars.put('\u00E3', "&atilde;");
        s_htmlEncodeChars.put('\u00E4', "&auml;");
        s_htmlEncodeChars.put('\u00E5', "&aring;");
        s_htmlEncodeChars.put('\u00E6', "&aelig;");
        s_htmlEncodeChars.put('\u00E7', "&ccedil;");
        s_htmlEncodeChars.put('\u00E8', "&egrave;");
        s_htmlEncodeChars.put('\u00E9', "&eacute;");
        s_htmlEncodeChars.put('\u00EA', "&ecirc;");
        s_htmlEncodeChars.put('\u00EB', "&euml;");
        s_htmlEncodeChars.put('\u00EC', "&igrave;");
        s_htmlEncodeChars.put('\u00ED', "&iacute;");
        s_htmlEncodeChars.put('\u00EE', "&icirc;");
        s_htmlEncodeChars.put('\u00EF', "&iuml;");
        s_htmlEncodeChars.put('\u00F0', "&eth;");
        s_htmlEncodeChars.put('\u00F1', "&ntilde;");
        s_htmlEncodeChars.put('\u00F2', "&ograve;");
        s_htmlEncodeChars.put('\u00F3', "&oacute;");
        s_htmlEncodeChars.put('\u00F4', "&ocirc;");
        s_htmlEncodeChars.put('\u00F5', "&otilde;");
        s_htmlEncodeChars.put('\u00F6', "&ouml;");
        s_htmlEncodeChars.put('\u00F7', "&divide;");
        s_htmlEncodeChars.put('\u00F8', "&oslash;");
        s_htmlEncodeChars.put('\u00F9', "&ugrave;");
        s_htmlEncodeChars.put('\u00FA', "&uacute;");
        s_htmlEncodeChars.put('\u00FB', "&ucirc;");
        s_htmlEncodeChars.put('\u00FC', "&uuml;");
        s_htmlEncodeChars.put('\u00FD', "&yacute;");
        s_htmlEncodeChars.put('\u00FE', "&thorn;");
        s_htmlEncodeChars.put('\u00FF', "&yuml;");

        // Mathematical, Greek and Symbolic characters for HTML
        s_htmlEncodeChars.put('\u0192', "&fnof;");
        s_htmlEncodeChars.put('\u0391', "&Alpha;");
        s_htmlEncodeChars.put('\u0392', "&Beta;");
        s_htmlEncodeChars.put('\u0393', "&Gamma;");
        s_htmlEncodeChars.put('\u0394', "&Delta;");
        s_htmlEncodeChars.put('\u0395', "&Epsilon;");
        s_htmlEncodeChars.put('\u0396', "&Zeta;");
        s_htmlEncodeChars.put('\u0397', "&Eta;");
        s_htmlEncodeChars.put('\u0398', "&Theta;");
        s_htmlEncodeChars.put('\u0399', "&Iota;");
        s_htmlEncodeChars.put('\u039A', "&Kappa;");
        s_htmlEncodeChars.put('\u039B', "&Lambda;");
        s_htmlEncodeChars.put('\u039C', "&Mu;");
        s_htmlEncodeChars.put('\u039D', "&Nu;");
        s_htmlEncodeChars.put('\u039E', "&Xi;");
        s_htmlEncodeChars.put('\u039F', "&Omicron;");
        s_htmlEncodeChars.put('\u03A0', "&Pi;");
        s_htmlEncodeChars.put('\u03A1', "&Rho;");
        s_htmlEncodeChars.put('\u03A3', "&Sigma;");
        s_htmlEncodeChars.put('\u03A4', "&Tau;");
        s_htmlEncodeChars.put('\u03A5', "&Upsilon;");
        s_htmlEncodeChars.put('\u03A6', "&Phi;");
        s_htmlEncodeChars.put('\u03A7', "&Chi;");
        s_htmlEncodeChars.put('\u03A8', "&Psi;");
        s_htmlEncodeChars.put('\u03A9', "&Omega;");
        s_htmlEncodeChars.put('\u03B1', "&alpha;");
        s_htmlEncodeChars.put('\u03B2', "&beta;");
        s_htmlEncodeChars.put('\u03B3', "&gamma;");
        s_htmlEncodeChars.put('\u03B4', "&delta;");
        s_htmlEncodeChars.put('\u03B5', "&epsilon;");
        s_htmlEncodeChars.put('\u03B6', "&zeta;");
        s_htmlEncodeChars.put('\u03B7', "&eta;");
        s_htmlEncodeChars.put('\u03B8', "&theta;");
        s_htmlEncodeChars.put('\u03B9', "&iota;");
        s_htmlEncodeChars.put('\u03BA', "&kappa;");
        s_htmlEncodeChars.put('\u03BB', "&lambda;");
        s_htmlEncodeChars.put('\u03BC', "&mu;");
        s_htmlEncodeChars.put('\u03BD', "&nu;");
        s_htmlEncodeChars.put('\u03BE', "&xi;");
        s_htmlEncodeChars.put('\u03BF', "&omicron;");
        s_htmlEncodeChars.put('\u03C0', "&pi;");
        s_htmlEncodeChars.put('\u03C1', "&rho;");
        s_htmlEncodeChars.put('\u03C2', "&sigmaf;");
        s_htmlEncodeChars.put('\u03C3', "&sigma;");
        s_htmlEncodeChars.put('\u03C4', "&tau;");
        s_htmlEncodeChars.put('\u03C5', "&upsilon;");
        s_htmlEncodeChars.put('\u03C6', "&phi;");
        s_htmlEncodeChars.put('\u03C7', "&chi;");
        s_htmlEncodeChars.put('\u03C8', "&psi;");
        s_htmlEncodeChars.put('\u03C9', "&omega;");
        s_htmlEncodeChars.put('\u03D1', "&thetasym;");
        s_htmlEncodeChars.put('\u03D2', "&upsih;");
        s_htmlEncodeChars.put('\u03D6', "&piv;");
        s_htmlEncodeChars.put('\u2022', "&bull;");
        s_htmlEncodeChars.put('\u2026', "&hellip;");
        s_htmlEncodeChars.put('\u2032', "&prime;");
        s_htmlEncodeChars.put('\u2033', "&Prime;");
        s_htmlEncodeChars.put('\u203E', "&oline;");
        s_htmlEncodeChars.put('\u2044', "&frasl;");
        s_htmlEncodeChars.put('\u2118', "&weierp;");
        s_htmlEncodeChars.put('\u2111', "&image;");
        s_htmlEncodeChars.put('\u211C', "&real;");
        s_htmlEncodeChars.put('\u2122', "&trade;");
        s_htmlEncodeChars.put('\u2135', "&alefsym;");
        s_htmlEncodeChars.put('\u2190', "&larr;");
        s_htmlEncodeChars.put('\u2191', "&uarr;");
        s_htmlEncodeChars.put('\u2192', "&rarr;");
        s_htmlEncodeChars.put('\u2193', "&darr;");
        s_htmlEncodeChars.put('\u2194', "&harr;");
        s_htmlEncodeChars.put('\u21B5', "&crarr;");
        s_htmlEncodeChars.put('\u21D0', "&lArr;");
        s_htmlEncodeChars.put('\u21D1', "&uArr;");
        s_htmlEncodeChars.put('\u21D2', "&rArr;");
        s_htmlEncodeChars.put('\u21D3', "&dArr;");
        s_htmlEncodeChars.put('\u21D4', "&hArr;");
        s_htmlEncodeChars.put('\u2200', "&forall;");
        s_htmlEncodeChars.put('\u2202', "&part;");
        s_htmlEncodeChars.put('\u2203', "&exist;");
        s_htmlEncodeChars.put('\u2205', "&empty;");
        s_htmlEncodeChars.put('\u2207', "&nabla;");
        s_htmlEncodeChars.put('\u2208', "&isin;");
        s_htmlEncodeChars.put('\u2209', "&notin;");
        s_htmlEncodeChars.put('\u220B', "&ni;");
        s_htmlEncodeChars.put('\u220F', "&prod;");
        s_htmlEncodeChars.put('\u2211', "&sum;");
        s_htmlEncodeChars.put('\u2212', "&minus;");
        s_htmlEncodeChars.put('\u2217', "&lowast;");
        s_htmlEncodeChars.put('\u221A', "&radic;");
        s_htmlEncodeChars.put('\u221D', "&prop;");
        s_htmlEncodeChars.put('\u221E', "&infin;");
        s_htmlEncodeChars.put('\u2220', "&ang;");
        s_htmlEncodeChars.put('\u2227', "&and;");
        s_htmlEncodeChars.put('\u2228', "&or;");
        s_htmlEncodeChars.put('\u2229', "&cap;");
        s_htmlEncodeChars.put('\u222A', "&cup;");
        s_htmlEncodeChars.put('\u222B', "&int;");
        s_htmlEncodeChars.put('\u2234', "&there4;");
        s_htmlEncodeChars.put('\u223C', "&sim;");
        s_htmlEncodeChars.put('\u2245', "&cong;");
        s_htmlEncodeChars.put('\u2248', "&asymp;");
        s_htmlEncodeChars.put('\u2260', "&ne;");
        s_htmlEncodeChars.put('\u2261', "&equiv;");
        s_htmlEncodeChars.put('\u2264', "&le;");
        s_htmlEncodeChars.put('\u2265', "&ge;");
        s_htmlEncodeChars.put('\u2282', "&sub;");
        s_htmlEncodeChars.put('\u2283', "&sup;");
        s_htmlEncodeChars.put('\u2284', "&nsub;");
        s_htmlEncodeChars.put('\u2286', "&sube;");
        s_htmlEncodeChars.put('\u2287', "&supe;");
        s_htmlEncodeChars.put('\u2295', "&oplus;");
        s_htmlEncodeChars.put('\u2297', "&otimes;");
        s_htmlEncodeChars.put('\u22A5', "&perp;");
        s_htmlEncodeChars.put('\u22C5', "&sdot;");
        s_htmlEncodeChars.put('\u2308', "&lceil;");
        s_htmlEncodeChars.put('\u2309', "&rceil;");
        s_htmlEncodeChars.put('\u230A', "&lfloor;");
        s_htmlEncodeChars.put('\u230B', "&rfloor;");
        s_htmlEncodeChars.put('\u2329', "&lang;");
        s_htmlEncodeChars.put('\u232A', "&rang;");
        s_htmlEncodeChars.put('\u25CA', "&loz;");
        s_htmlEncodeChars.put('\u2660', "&spades;");
        s_htmlEncodeChars.put('\u2663', "&clubs;");
        s_htmlEncodeChars.put('\u2665', "&hearts;");
        s_htmlEncodeChars.put('\u2666', "&diams;");
    }

    /**
     * Encode the HTML-significant characters in the given string as HTML character entities.
     *
     * @param _source
     *            the string to encode; may be null
     * @return the encoded string, or null if _source was null
     */
    public static String encodeHtml(final String _source) {
        return encode(_source, s_htmlEncodeChars);
    }

    /**
     * Replace every character in the given string that appears in the encoding table with its mapped
     * replacement. Characters not in the table are copied through unchanged.
     *
     * @param _source
     *            the string to encode; may be null
     * @param _encodingTable
     *            the map of characters to their replacement strings; may be null
     * @return the encoded string; null if _source was null, or _source itself if nothing needed replacing or
     *         _encodingTable was null
     */
    private static String encode(final String _source, final HashMap<Character, String> _encodingTable) {

        if (null == _source) {
            return null;
        }

        if (null == _encodingTable) {
            return _source;
        }

        final char[] stringToEncodeArray = _source.toCharArray();
        StringBuffer encodedString = null;
        int lastMatch = -1;
        int difference = 0;

        for (int i = 0; i < stringToEncodeArray.length; i++) {
            final char charToEncode = stringToEncodeArray[i];

            if (_encodingTable.containsKey(charToEncode)) {

                if (null == encodedString) {
                    encodedString = new StringBuffer(_source.length());
                }

                difference = i - (lastMatch + 1);
                if (difference > 0) {
                    encodedString.append(stringToEncodeArray, lastMatch + 1, difference);
                }

                encodedString.append(_encodingTable.get(charToEncode));
                lastMatch = i;
            }
        }

        if (null == encodedString) {
            return _source;
        }

        difference = stringToEncodeArray.length - (lastMatch + 1);
        if (difference > 0) {
            encodedString.append(stringToEncodeArray, lastMatch + 1, difference);
        }

        return encodedString.toString();
    }
}
