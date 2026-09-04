package com.github.ibm.mapepire;

import java.util.HashMap;

// Derived from https://howtodoinjava.com/java/string/escape-html-encode-string/
// Presumed to be proper and valid fair use
class TraceHtmlEncoder {

    private static final HashMap<Character, String> HTML_ENCODE_CHARS = new HashMap<>();

    static {
        // Special characters for HTML
        HTML_ENCODE_CHARS.put('\u0026', "&amp;");
        HTML_ENCODE_CHARS.put('\u003C', "&lt;");
        HTML_ENCODE_CHARS.put('\u003E', "&gt;");
        HTML_ENCODE_CHARS.put('\u0022', "&quot;");
        HTML_ENCODE_CHARS.put('\u0152', "&OElig;");
        HTML_ENCODE_CHARS.put('\u0153', "&oelig;");
        HTML_ENCODE_CHARS.put('\u0160', "&Scaron;");
        HTML_ENCODE_CHARS.put('\u0161', "&scaron;");
        HTML_ENCODE_CHARS.put('\u0178', "&Yuml;");
        HTML_ENCODE_CHARS.put('\u02C6', "&circ;");
        HTML_ENCODE_CHARS.put('\u02DC', "&tilde;");
        HTML_ENCODE_CHARS.put('\u2002', "&ensp;");
        HTML_ENCODE_CHARS.put('\u2003', "&emsp;");
        HTML_ENCODE_CHARS.put('\u2009', "&thinsp;");
        HTML_ENCODE_CHARS.put('\u200C', "&zwnj;");
        HTML_ENCODE_CHARS.put('\u200D', "&zwj;");
        HTML_ENCODE_CHARS.put('\u200E', "&lrm;");
        HTML_ENCODE_CHARS.put('\u200F', "&rlm;");
        HTML_ENCODE_CHARS.put('\u2013', "&ndash;");
        HTML_ENCODE_CHARS.put('\u2014', "&mdash;");
        HTML_ENCODE_CHARS.put('\u2018', "&lsquo;");
        HTML_ENCODE_CHARS.put('\u2019', "&rsquo;");
        HTML_ENCODE_CHARS.put('\u201A', "&sbquo;");
        HTML_ENCODE_CHARS.put('\u201C', "&ldquo;");
        HTML_ENCODE_CHARS.put('\u201D', "&rdquo;");
        HTML_ENCODE_CHARS.put('\u201E', "&bdquo;");
        HTML_ENCODE_CHARS.put('\u2020', "&dagger;");
        HTML_ENCODE_CHARS.put('\u2021', "&Dagger;");
        HTML_ENCODE_CHARS.put('\u2030', "&permil;");
        HTML_ENCODE_CHARS.put('\u2039', "&lsaquo;");
        HTML_ENCODE_CHARS.put('\u203A', "&rsaquo;");
        HTML_ENCODE_CHARS.put('\u20AC', "&euro;");

        // Character entity references for ISO 8859-1 characters
        HTML_ENCODE_CHARS.put('\u00A0', "&nbsp;");
        HTML_ENCODE_CHARS.put('\u00A1', "&iexcl;");
        HTML_ENCODE_CHARS.put('\u00A2', "&cent;");
        HTML_ENCODE_CHARS.put('\u00A3', "&pound;");
        HTML_ENCODE_CHARS.put('\u00A4', "&curren;");
        HTML_ENCODE_CHARS.put('\u00A5', "&yen;");
        HTML_ENCODE_CHARS.put('\u00A6', "&brvbar;");
        HTML_ENCODE_CHARS.put('\u00A7', "&sect;");
        HTML_ENCODE_CHARS.put('\u00A8', "&uml;");
        HTML_ENCODE_CHARS.put('\u00A9', "&copy;");
        HTML_ENCODE_CHARS.put('\u00AA', "&ordf;");
        HTML_ENCODE_CHARS.put('\u00AB', "&laquo;");
        HTML_ENCODE_CHARS.put('\u00AC', "&not;");
        HTML_ENCODE_CHARS.put('\u00AD', "&shy;");
        HTML_ENCODE_CHARS.put('\u00AE', "&reg;");
        HTML_ENCODE_CHARS.put('\u00AF', "&macr;");
        HTML_ENCODE_CHARS.put('\u00B0', "&deg;");
        HTML_ENCODE_CHARS.put('\u00B1', "&plusmn;");
        HTML_ENCODE_CHARS.put('\u00B2', "&sup2;");
        HTML_ENCODE_CHARS.put('\u00B3', "&sup3;");
        HTML_ENCODE_CHARS.put('\u00B4', "&acute;");
        HTML_ENCODE_CHARS.put('\u00B5', "&micro;");
        HTML_ENCODE_CHARS.put('\u00B6', "&para;");
        HTML_ENCODE_CHARS.put('\u00B7', "&middot;");
        HTML_ENCODE_CHARS.put('\u00B8', "&cedil;");
        HTML_ENCODE_CHARS.put('\u00B9', "&sup1;");
        HTML_ENCODE_CHARS.put('\u00BA', "&ordm;");
        HTML_ENCODE_CHARS.put('\u00BB', "&raquo;");
        HTML_ENCODE_CHARS.put('\u00BC', "&frac14;");
        HTML_ENCODE_CHARS.put('\u00BD', "&frac12;");
        HTML_ENCODE_CHARS.put('\u00BE', "&frac34;");
        HTML_ENCODE_CHARS.put('\u00BF', "&iquest;");
        HTML_ENCODE_CHARS.put('\u00C0', "&Agrave;");
        HTML_ENCODE_CHARS.put('\u00C1', "&Aacute;");
        HTML_ENCODE_CHARS.put('\u00C2', "&Acirc;");
        HTML_ENCODE_CHARS.put('\u00C3', "&Atilde;");
        HTML_ENCODE_CHARS.put('\u00C4', "&Auml;");
        HTML_ENCODE_CHARS.put('\u00C5', "&Aring;");
        HTML_ENCODE_CHARS.put('\u00C6', "&AElig;");
        HTML_ENCODE_CHARS.put('\u00C7', "&Ccedil;");
        HTML_ENCODE_CHARS.put('\u00C8', "&Egrave;");
        HTML_ENCODE_CHARS.put('\u00C9', "&Eacute;");
        HTML_ENCODE_CHARS.put('\u00CA', "&Ecirc;");
        HTML_ENCODE_CHARS.put('\u00CB', "&Euml;");
        HTML_ENCODE_CHARS.put('\u00CC', "&Igrave;");
        HTML_ENCODE_CHARS.put('\u00CD', "&Iacute;");
        HTML_ENCODE_CHARS.put('\u00CE', "&Icirc;");
        HTML_ENCODE_CHARS.put('\u00CF', "&Iuml;");
        HTML_ENCODE_CHARS.put('\u00D0', "&ETH;");
        HTML_ENCODE_CHARS.put('\u00D1', "&Ntilde;");
        HTML_ENCODE_CHARS.put('\u00D2', "&Ograve;");
        HTML_ENCODE_CHARS.put('\u00D3', "&Oacute;");
        HTML_ENCODE_CHARS.put('\u00D4', "&Ocirc;");
        HTML_ENCODE_CHARS.put('\u00D5', "&Otilde;");
        HTML_ENCODE_CHARS.put('\u00D6', "&Ouml;");
        HTML_ENCODE_CHARS.put('\u00D7', "&times;");
        HTML_ENCODE_CHARS.put('\u00D8', "&Oslash;");
        HTML_ENCODE_CHARS.put('\u00D9', "&Ugrave;");
        HTML_ENCODE_CHARS.put('\u00DA', "&Uacute;");
        HTML_ENCODE_CHARS.put('\u00DB', "&Ucirc;");
        HTML_ENCODE_CHARS.put('\u00DC', "&Uuml;");
        HTML_ENCODE_CHARS.put('\u00DD', "&Yacute;");
        HTML_ENCODE_CHARS.put('\u00DE', "&THORN;");
        HTML_ENCODE_CHARS.put('\u00DF', "&szlig;");
        HTML_ENCODE_CHARS.put('\u00E0', "&agrave;");
        HTML_ENCODE_CHARS.put('\u00E1', "&aacute;");
        HTML_ENCODE_CHARS.put('\u00E2', "&acirc;");
        HTML_ENCODE_CHARS.put('\u00E3', "&atilde;");
        HTML_ENCODE_CHARS.put('\u00E4', "&auml;");
        HTML_ENCODE_CHARS.put('\u00E5', "&aring;");
        HTML_ENCODE_CHARS.put('\u00E6', "&aelig;");
        HTML_ENCODE_CHARS.put('\u00E7', "&ccedil;");
        HTML_ENCODE_CHARS.put('\u00E8', "&egrave;");
        HTML_ENCODE_CHARS.put('\u00E9', "&eacute;");
        HTML_ENCODE_CHARS.put('\u00EA', "&ecirc;");
        HTML_ENCODE_CHARS.put('\u00EB', "&euml;");
        HTML_ENCODE_CHARS.put('\u00EC', "&igrave;");
        HTML_ENCODE_CHARS.put('\u00ED', "&iacute;");
        HTML_ENCODE_CHARS.put('\u00EE', "&icirc;");
        HTML_ENCODE_CHARS.put('\u00EF', "&iuml;");
        HTML_ENCODE_CHARS.put('\u00F0', "&eth;");
        HTML_ENCODE_CHARS.put('\u00F1', "&ntilde;");
        HTML_ENCODE_CHARS.put('\u00F2', "&ograve;");
        HTML_ENCODE_CHARS.put('\u00F3', "&oacute;");
        HTML_ENCODE_CHARS.put('\u00F4', "&ocirc;");
        HTML_ENCODE_CHARS.put('\u00F5', "&otilde;");
        HTML_ENCODE_CHARS.put('\u00F6', "&ouml;");
        HTML_ENCODE_CHARS.put('\u00F7', "&divide;");
        HTML_ENCODE_CHARS.put('\u00F8', "&oslash;");
        HTML_ENCODE_CHARS.put('\u00F9', "&ugrave;");
        HTML_ENCODE_CHARS.put('\u00FA', "&uacute;");
        HTML_ENCODE_CHARS.put('\u00FB', "&ucirc;");
        HTML_ENCODE_CHARS.put('\u00FC', "&uuml;");
        HTML_ENCODE_CHARS.put('\u00FD', "&yacute;");
        HTML_ENCODE_CHARS.put('\u00FE', "&thorn;");
        HTML_ENCODE_CHARS.put('\u00FF', "&yuml;");

        // Mathematical, Greek and Symbolic characters for HTML
        HTML_ENCODE_CHARS.put('\u0192', "&fnof;");
        HTML_ENCODE_CHARS.put('\u0391', "&Alpha;");
        HTML_ENCODE_CHARS.put('\u0392', "&Beta;");
        HTML_ENCODE_CHARS.put('\u0393', "&Gamma;");
        HTML_ENCODE_CHARS.put('\u0394', "&Delta;");
        HTML_ENCODE_CHARS.put('\u0395', "&Epsilon;");
        HTML_ENCODE_CHARS.put('\u0396', "&Zeta;");
        HTML_ENCODE_CHARS.put('\u0397', "&Eta;");
        HTML_ENCODE_CHARS.put('\u0398', "&Theta;");
        HTML_ENCODE_CHARS.put('\u0399', "&Iota;");
        HTML_ENCODE_CHARS.put('\u039A', "&Kappa;");
        HTML_ENCODE_CHARS.put('\u039B', "&Lambda;");
        HTML_ENCODE_CHARS.put('\u039C', "&Mu;");
        HTML_ENCODE_CHARS.put('\u039D', "&Nu;");
        HTML_ENCODE_CHARS.put('\u039E', "&Xi;");
        HTML_ENCODE_CHARS.put('\u039F', "&Omicron;");
        HTML_ENCODE_CHARS.put('\u03A0', "&Pi;");
        HTML_ENCODE_CHARS.put('\u03A1', "&Rho;");
        HTML_ENCODE_CHARS.put('\u03A3', "&Sigma;");
        HTML_ENCODE_CHARS.put('\u03A4', "&Tau;");
        HTML_ENCODE_CHARS.put('\u03A5', "&Upsilon;");
        HTML_ENCODE_CHARS.put('\u03A6', "&Phi;");
        HTML_ENCODE_CHARS.put('\u03A7', "&Chi;");
        HTML_ENCODE_CHARS.put('\u03A8', "&Psi;");
        HTML_ENCODE_CHARS.put('\u03A9', "&Omega;");
        HTML_ENCODE_CHARS.put('\u03B1', "&alpha;");
        HTML_ENCODE_CHARS.put('\u03B2', "&beta;");
        HTML_ENCODE_CHARS.put('\u03B3', "&gamma;");
        HTML_ENCODE_CHARS.put('\u03B4', "&delta;");
        HTML_ENCODE_CHARS.put('\u03B5', "&epsilon;");
        HTML_ENCODE_CHARS.put('\u03B6', "&zeta;");
        HTML_ENCODE_CHARS.put('\u03B7', "&eta;");
        HTML_ENCODE_CHARS.put('\u03B8', "&theta;");
        HTML_ENCODE_CHARS.put('\u03B9', "&iota;");
        HTML_ENCODE_CHARS.put('\u03BA', "&kappa;");
        HTML_ENCODE_CHARS.put('\u03BB', "&lambda;");
        HTML_ENCODE_CHARS.put('\u03BC', "&mu;");
        HTML_ENCODE_CHARS.put('\u03BD', "&nu;");
        HTML_ENCODE_CHARS.put('\u03BE', "&xi;");
        HTML_ENCODE_CHARS.put('\u03BF', "&omicron;");
        HTML_ENCODE_CHARS.put('\u03C0', "&pi;");
        HTML_ENCODE_CHARS.put('\u03C1', "&rho;");
        HTML_ENCODE_CHARS.put('\u03C2', "&sigmaf;");
        HTML_ENCODE_CHARS.put('\u03C3', "&sigma;");
        HTML_ENCODE_CHARS.put('\u03C4', "&tau;");
        HTML_ENCODE_CHARS.put('\u03C5', "&upsilon;");
        HTML_ENCODE_CHARS.put('\u03C6', "&phi;");
        HTML_ENCODE_CHARS.put('\u03C7', "&chi;");
        HTML_ENCODE_CHARS.put('\u03C8', "&psi;");
        HTML_ENCODE_CHARS.put('\u03C9', "&omega;");
        HTML_ENCODE_CHARS.put('\u03D1', "&thetasym;");
        HTML_ENCODE_CHARS.put('\u03D2', "&upsih;");
        HTML_ENCODE_CHARS.put('\u03D6', "&piv;");
        HTML_ENCODE_CHARS.put('\u2022', "&bull;");
        HTML_ENCODE_CHARS.put('\u2026', "&hellip;");
        HTML_ENCODE_CHARS.put('\u2032', "&prime;");
        HTML_ENCODE_CHARS.put('\u2033', "&Prime;");
        HTML_ENCODE_CHARS.put('\u203E', "&oline;");
        HTML_ENCODE_CHARS.put('\u2044', "&frasl;");
        HTML_ENCODE_CHARS.put('\u2118', "&weierp;");
        HTML_ENCODE_CHARS.put('\u2111', "&image;");
        HTML_ENCODE_CHARS.put('\u211C', "&real;");
        HTML_ENCODE_CHARS.put('\u2122', "&trade;");
        HTML_ENCODE_CHARS.put('\u2135', "&alefsym;");
        HTML_ENCODE_CHARS.put('\u2190', "&larr;");
        HTML_ENCODE_CHARS.put('\u2191', "&uarr;");
        HTML_ENCODE_CHARS.put('\u2192', "&rarr;");
        HTML_ENCODE_CHARS.put('\u2193', "&darr;");
        HTML_ENCODE_CHARS.put('\u2194', "&harr;");
        HTML_ENCODE_CHARS.put('\u21B5', "&crarr;");
        HTML_ENCODE_CHARS.put('\u21D0', "&lArr;");
        HTML_ENCODE_CHARS.put('\u21D1', "&uArr;");
        HTML_ENCODE_CHARS.put('\u21D2', "&rArr;");
        HTML_ENCODE_CHARS.put('\u21D3', "&dArr;");
        HTML_ENCODE_CHARS.put('\u21D4', "&hArr;");
        HTML_ENCODE_CHARS.put('\u2200', "&forall;");
        HTML_ENCODE_CHARS.put('\u2202', "&part;");
        HTML_ENCODE_CHARS.put('\u2203', "&exist;");
        HTML_ENCODE_CHARS.put('\u2205', "&empty;");
        HTML_ENCODE_CHARS.put('\u2207', "&nabla;");
        HTML_ENCODE_CHARS.put('\u2208', "&isin;");
        HTML_ENCODE_CHARS.put('\u2209', "&notin;");
        HTML_ENCODE_CHARS.put('\u220B', "&ni;");
        HTML_ENCODE_CHARS.put('\u220F', "&prod;");
        HTML_ENCODE_CHARS.put('\u2211', "&sum;");
        HTML_ENCODE_CHARS.put('\u2212', "&minus;");
        HTML_ENCODE_CHARS.put('\u2217', "&lowast;");
        HTML_ENCODE_CHARS.put('\u221A', "&radic;");
        HTML_ENCODE_CHARS.put('\u221D', "&prop;");
        HTML_ENCODE_CHARS.put('\u221E', "&infin;");
        HTML_ENCODE_CHARS.put('\u2220', "&ang;");
        HTML_ENCODE_CHARS.put('\u2227', "&and;");
        HTML_ENCODE_CHARS.put('\u2228', "&or;");
        HTML_ENCODE_CHARS.put('\u2229', "&cap;");
        HTML_ENCODE_CHARS.put('\u222A', "&cup;");
        HTML_ENCODE_CHARS.put('\u222B', "&int;");
        HTML_ENCODE_CHARS.put('\u2234', "&there4;");
        HTML_ENCODE_CHARS.put('\u223C', "&sim;");
        HTML_ENCODE_CHARS.put('\u2245', "&cong;");
        HTML_ENCODE_CHARS.put('\u2248', "&asymp;");
        HTML_ENCODE_CHARS.put('\u2260', "&ne;");
        HTML_ENCODE_CHARS.put('\u2261', "&equiv;");
        HTML_ENCODE_CHARS.put('\u2264', "&le;");
        HTML_ENCODE_CHARS.put('\u2265', "&ge;");
        HTML_ENCODE_CHARS.put('\u2282', "&sub;");
        HTML_ENCODE_CHARS.put('\u2283', "&sup;");
        HTML_ENCODE_CHARS.put('\u2284', "&nsub;");
        HTML_ENCODE_CHARS.put('\u2286', "&sube;");
        HTML_ENCODE_CHARS.put('\u2287', "&supe;");
        HTML_ENCODE_CHARS.put('\u2295', "&oplus;");
        HTML_ENCODE_CHARS.put('\u2297', "&otimes;");
        HTML_ENCODE_CHARS.put('\u22A5', "&perp;");
        HTML_ENCODE_CHARS.put('\u22C5', "&sdot;");
        HTML_ENCODE_CHARS.put('\u2308', "&lceil;");
        HTML_ENCODE_CHARS.put('\u2309', "&rceil;");
        HTML_ENCODE_CHARS.put('\u230A', "&lfloor;");
        HTML_ENCODE_CHARS.put('\u230B', "&rfloor;");
        HTML_ENCODE_CHARS.put('\u2329', "&lang;");
        HTML_ENCODE_CHARS.put('\u232A', "&rang;");
        HTML_ENCODE_CHARS.put('\u25CA', "&loz;");
        HTML_ENCODE_CHARS.put('\u2660', "&spades;");
        HTML_ENCODE_CHARS.put('\u2663', "&clubs;");
        HTML_ENCODE_CHARS.put('\u2665', "&hearts;");
        HTML_ENCODE_CHARS.put('\u2666', "&diams;");
    }

    /**
     * Encode the HTML-significant characters in the given string as HTML character entities.
     *
     * @param _source
     *            the string to encode; may be null
     * @return the encoded string, or null if _source was null
     */
    public static String encodeHtml(final String _source) {
        return encode(_source, HTML_ENCODE_CHARS);
    }

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
