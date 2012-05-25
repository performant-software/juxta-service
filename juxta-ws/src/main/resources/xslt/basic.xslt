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
    <xsl:template match="{LB_LIST}">
        <xsl:call-template name="line-break"/>
    </xsl:template>    

    <xsl:template match="text()">
         <!-- Rules: Newlines bound the formatting whitespace. At the start of
              a line, all space prior to the newline is to be kept. All is formatting.
              At the end of a line, all whitespace after the last newline is to be stripped -
              includinf the newline itself -->
         <!-- first, left trim the original text to eliminate xml-formatting whitespace -->     
         <xsl:variable name="left-trimmed">
             <!-- if the first char is a space, we want to preserve it -->
             <xsl:variable name="tmp" select="substring(., 1, 1)"/>
             <xsl:choose>
                <xsl:when test="$tmp = ' ' or $tmp = $tab">
                    <!-- extract the leading non-formattng-space pad for this element -->
                    <xsl:variable name="pad">
                        <xsl:call-template name="find-left-pad">
                            <xsl:with-param name="string" select="." />
                        </xsl:call-template>
                    </xsl:variable>      
                    <!-- left trim all whitespace and add the pad found above -->
                    <xsl:call-template name="left-trim">
                        <xsl:with-param name="string" select="." />
                        <xsl:with-param name="pad" select="$pad" />
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
    
    <!-- find all of the leading space characters up until newline or non-whitespace -->
    <xsl:template name="find-left-pad">
        <xsl:param name="string" select="''"/>
        <xsl:param name="pad" select="''"/>
        <xsl:variable name="tmp" select="substring($string, 1, 1)"/>
        <xsl:choose>
            <xsl:when test="$tmp = ' '  or $tmp = $tab">
                <xsl:variable name="new-pad" select="concat($pad, ' ')"/>
                <xsl:call-template name="find-left-pad">
                    <xsl:with-param name="string" select="substring-after($string, $tmp)"/>
                    <xsl:with-param name="pad" select="$new-pad"/>
                </xsl:call-template>
            </xsl:when>
            <xsl:otherwise>
                <xsl:value-of select="$pad"/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>
    
    <!-- remove all leading whitespace and replace with the specified pad -->
    <xsl:template name="left-trim">
        <xsl:param name="string" select="''"/>
        <xsl:param name="pad" select="''"/>
        <xsl:variable name="tmp" select="substring($string, 1, 1)"/>
        <xsl:choose>
            <xsl:when test="normalize-space($tmp) = ''">
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
    
    <!-- remove all trailing whitespace from an element -->
    <xsl:template name="right-trim">
        <xsl:param name="string" select="''"/>
        <xsl:variable name="tmp" select="substring($string, string-length($string), 1)"/>
        <xsl:choose>
            <xsl:when test="$tmp = ' ' or $tmp = $tab">
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
        <xsl:value-of select="$display-linebreak"/>
    </xsl:template>
</xsl:stylesheet>