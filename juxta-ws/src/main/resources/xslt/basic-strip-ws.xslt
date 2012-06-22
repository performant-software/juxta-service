<?xml version="1.0" encoding="utf-8"?>
<xsl:stylesheet version="1.0" 
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                {NAMESPACE}>
    <xsl:output method="text" indent="no"/>
    <xsl:strip-space elements="*"/>
    <xsl:variable name="new-line" select="'&#10;'" />
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
         <xsl:variable name="leading">
             <xsl:variable name="tmp" select="substring(., 1, 1)"/>
             <xsl:choose>
                <xsl:when test="$tmp = ' ' or $tmp = $tab">
                   <xsl:value-of select="' '"/>
                </xsl:when>
                <xsl:otherwise>
                   <xsl:value-of select="''"/>
                </xsl:otherwise>
            </xsl:choose>
         </xsl:variable>
         
         <xsl:variable name="trailing">
             <xsl:variable name="tmp" select="substring(., string-length(.), 1)"/>
             <xsl:choose>
                <xsl:when test="$tmp = ' ' or $tmp = $tab">
                   <xsl:value-of select="' '"/>
                </xsl:when>
                <xsl:otherwise>
                   <xsl:value-of select="''"/>
                </xsl:otherwise>
             </xsl:choose>
         </xsl:variable>
    
         <xsl:variable name="normalized" select="normalize-space(.)"/>
         <xsl:choose>
            <xsl:when test="string-length($normalized) > 0">
               <xsl:variable name="padded" select="concat( $leading, concat($normalized, $trailing))"/>
               <xsl:value-of select="$padded"/>   
            </xsl:when>
            <xsl:otherwise>
               <xsl:value-of select="$normalized"/>
            </xsl:otherwise>
         </xsl:choose>
             
    </xsl:template>
        
    <xsl:template name="line-break">
        <xsl:apply-templates/>
        <xsl:value-of select="$display-linebreak"/>
    </xsl:template>
</xsl:stylesheet>