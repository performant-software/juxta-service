<?xml version="1.0" encoding="utf-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" {NAMESPACE}>
    <xsl:output method="text" indent="no"/>
    <xsl:strip-space elements="*"/>
    <xsl:variable name="new-line" select="'&#10;'" />
    <xsl:variable name="marker" select="'&#x2573;'" />
    <xsl:variable name="tab" select="'&#9;'" />
    <xsl:variable name="display-linebreak" select="'{LINEBREAK}'" />
    
    <!--special-handling-->
    <xsl:template match="{NOTE}"/>
    <xsl:template match="{PB}">
        <xsl:value-of select="$display-linebreak"/>
    </xsl:template>

    <!--global-exclusions-->
    <!--single-exclusions-->
    <!--breaks-->
    
    <xsl:template match="text()">
        <!-- turn newline into special char so its not treated as whitespace -->
        <xsl:variable name="fixed" select="translate(., $new-line, $marker)"/>

        <!-- find any leading white space in front of the nl. preserve 1 -->
        <xsl:variable name="leading">
            <xsl:variable name="tmp" select="substring($fixed, 1, 1)"/>
            <xsl:choose>
                <xsl:when test="$tmp = ' ' or $tmp = $tab">
                    <xsl:value-of select="' '"/>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:value-of select="''"/>
                </xsl:otherwise>
            </xsl:choose>
        </xsl:variable>

        <!-- see if there is a trailing space on the line -->
        <xsl:variable name="trailing">
            <xsl:choose>
                <!-- if there are any nl markers, normalize to strip trailing spaces
                     and compress thise before the marker to 1. preserve it if found -->
                <xsl:when test="contains($fixed, $marker)">
                    <xsl:call-template name="get-trailing">
                        <xsl:with-param name="string" select="normalize-space($fixed)" />
                    </xsl:call-template>
                </xsl:when>
                <xsl:otherwise>
                    <!-- no markers. do not normalize here - it would eat the trailing spaces -->
                    <xsl:call-template name="get-trailing">
                        <xsl:with-param name="string" select="$fixed" />
                    </xsl:call-template>
                </xsl:otherwise>
            </xsl:choose>
        </xsl:variable>

        <!-- end result: normalize the original src and append leading/trailing space -->
        <xsl:value-of select="concat(concat($leading, normalize-space(.)),$trailing)"/>
    </xsl:template>

    <!-- return ' ' if trailing char is a ' ' -->
    <xsl:template name="get-trailing">
        <xsl:param name="string" select="''"/>
        <xsl:variable name="tmp1" select="translate($string, $marker, '')"/>
        <xsl:variable name="tmp" select="substring($tmp1, string-length($tmp1), 1)"/>
        <xsl:choose>
            <xsl:when test="$tmp = ' ' or $tmp = $tab">
                <xsl:value-of select="' '"/>
            </xsl:when>
            <xsl:otherwise>
                <xsl:value-of select="''"/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>
    
    <xsl:template name="line-break">
        <xsl:apply-templates/>
        <xsl:value-of select="$display-linebreak"/>
    </xsl:template>
</xsl:stylesheet>