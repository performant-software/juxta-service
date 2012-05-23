<?xml version="1.0" encoding="utf-8"?>
<xsl:stylesheet version="1.0" 
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    <xsl:output method="text" indent="no"/>
    <xsl:strip-space elements="*"/>
    <xsl:variable name="new-line" select="'&#10;'" />
    <!--global-exclusions-->
    <!--single-exclusions-->
    <!--breaks-->   
    <xsl:template match="*">
        <xsl:call-template name="line-break"/>
    </xsl:template>    

    <xsl:template match="text()">
         <!-- Rules: Newlines bound the formatting whitespace. At the start of
              a line, all space prior to the newline is to be kept. All is formatting.
              At the end of a line, all whitespace after the last newline is to be stripped -
              includinf the newline itself -->
         <!-- first, left trim the original text to eliminate xml-formatting whitespace -->     
         <xsl:variable name="left-trimmed">
             <xsl:choose>
                <!-- if the first char is a space, we want to preserve it -->
                <xsl:when test="substring(.,1,1) = ' '">
                    <xsl:call-template name="left-trim">
                        <xsl:with-param name="string" select="." />
                        <xsl:with-param name="pad" select="' '" />
                    </xsl:call-template>
                </xsl:when>
                <xsl:otherwise>
                    <!-- first char is non-space. strip all whitespace -->
                    <xsl:call-template name="left-trim">
                        <xsl:with-param name="string" select="." />
                        <xsl:with-param name="pad" select="''" />
                    </xsl:call-template>
                </xsl:otherwise>
             </xsl:choose>
         </xsl:variable>
         
         <!-- next, right trim any unwanted space -->
         <xsl:choose>
             <!-- if there is anything before the last newline, throw away evertying after that
                  newline (including the newline itself -->
             <xsl:when test="substring-before($left-trimmed,$new-line)">
                  <xsl:variable name="trimmed">
                    <xsl:call-template name="right-trim">
                        <xsl:with-param name="string" select="$left-trimmed" />
                    </xsl:call-template>
                 </xsl:variable>
                 <xsl:value-of select="$trimmed"/>
             </xsl:when>
             <xsl:otherwise>
                 <!-- nothing after the last newline, so nothing to trim -->
                <xsl:value-of select="$left-trimmed"/>
             </xsl:otherwise>
         </xsl:choose>

    </xsl:template>
    
    <xsl:template name="left-trim">
        <xsl:param name="string" select="''"/>
        <xsl:param name="pad" select="''"/>
        <xsl:variable name="tmp" select="substring($string, 1, 1)"/>
        <xsl:choose>
            <xsl:when test="$tmp = ' ' or $tmp = $new-line">
                <xsl:call-template name="left-trim">
                    <xsl:with-param name="string" select="substring-after($string, $tmp)"/>
                    <xsl:with-param name="pad" select="$pad"/>
                </xsl:call-template>
            </xsl:when>
            <xsl:otherwise>
                <xsl:value-of select="concat($pad,$string)"/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>
    
    <xsl:template name="right-trim">
        <xsl:param name="string" select="''"/>
        <xsl:variable name="tmp" select="substring($string, string-length($string), 1)"/>
        <xsl:choose>
            <xsl:when test="$tmp = ' '">
                <xsl:call-template name="right-trim">
                    <xsl:with-param name="string" select="substring($string, 1, string-length($string)-1)"/>
                </xsl:call-template>
            </xsl:when>
            <xsl:otherwise>
                <xsl:choose>
                     <xsl:when test="$tmp = $new-line">
                        <xsl:value-of select="substring($string, 1, string-length($string)-1)"/>
                     </xsl:when>
                     <xsl:otherwise>
                         <xsl:value-of select="$string"/>
                     </xsl:otherwise>
                </xsl:choose>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>
        
    <xsl:template name="line-break">
        <xsl:apply-templates/>
        <xsl:text>&#10;</xsl:text>
    </xsl:template>
</xsl:stylesheet>