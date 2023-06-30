package com.github.theprez.codefori;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.github.theprez.jcmdutils.StringUtils;
import com.ibm.as400.access.QSYSObjectPathName;

public class IBMiObjectPathNormalizer {
    private static Pattern m_ifsFormat = Pattern.compile("^(/+qsys.lib)?/+([^\\/\\(\\(\\s]{1,10})\\.lib/+([^\\/\\(\\(\\s]{1,10})\\.([^\\/\\(\\(\\s]{1,10})(/+([^\\/\\(\\(\\s]{1,10})\\.mbr)?$", Pattern.CASE_INSENSITIVE);
    private static Pattern m_libObjFormat = Pattern.compile("^(([^\\/\\(\\(\\s]{1,10})\\s*\\/)?\\s*([^\\/\\(\\(\\s]{1,10})\\s*(\\(\\s*([^\\/\\(\\(\\s]{1,10})\\s*\\)\\s*)?\\s*$");

    public static Pattern getIfsFormat() {
        return m_ifsFormat;
    }

    public static Pattern getM_libObjFormat() {
        return m_libObjFormat;
    }

    private final String m_ifsPath;
    private final String m_lib;
    private final String m_mbr;

    private final String m_obj;

    private final String m_type;

    public IBMiObjectPathNormalizer(final String _path, final String _objType) {
        Matcher m = m_libObjFormat.matcher(_path);
        if (m.matches()) {
            m_type = _objType.replaceFirst("^(\\*|\\.)", "");
            final String lib = m.group(2);
            final boolean isLibEmpty = StringUtils.isEmpty(lib);
            m_lib = isLibEmpty ? "*LIBL" : lib;
            m_obj = m.group(3);
            m_mbr = m.group(5);
            if (isLibEmpty) {
                m_ifsPath = null;
            } else {
                if (StringUtils.isNonEmpty(m_mbr)) {
                    m_ifsPath = new QSYSObjectPathName(m_lib, m_obj, m_mbr, m_type).getPath();
                } else {
                    m_ifsPath = new QSYSObjectPathName(m_lib, m_obj, m_type).getPath();
                }
            }
        } else {
            m = m_ifsFormat.matcher(_path);
            if (m.matches()) {
                m_lib = m.group(2);
                m_obj = m.group(3);
                m_type = m.group(4);
                m_mbr = m.group(6);

                if (StringUtils.isNonEmpty(m_mbr)) {
                    m_ifsPath = new QSYSObjectPathName(m_lib, m_obj, m_mbr, _objType.replaceFirst("^(\\*|.)", "")).getPath();
                } else {
                    m_ifsPath = new QSYSObjectPathName(m_lib, m_obj, _objType.replaceFirst("^(\\*|.)", "")).getPath();
                }
            } else {
                m_ifsPath = _path;
                m_lib = null;
                m_obj = null;
                m_mbr = null;
                m_type = "STMF";
            }
        }
    }

    public String getIfsPath() {
        return m_ifsPath;
    }

    public String getLibrary() {
        return m_lib;
    }

    public String getMbr() {
        return m_mbr;
    }

    public String getObjName() {
        return m_obj;
    }

    public String getType() {
        return m_type;
    }
}
