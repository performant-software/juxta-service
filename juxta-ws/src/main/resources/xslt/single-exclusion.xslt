<xsl:template match="{TAG}">
        <xsl:variable name="count" select="1 + count(ancestor::{TAG})+count(preceding::{TAG})"/>
        <xsl:if test="{CONDITION}">
            <xsl:apply-templates/>
        </xsl:if>
    </xsl:template>