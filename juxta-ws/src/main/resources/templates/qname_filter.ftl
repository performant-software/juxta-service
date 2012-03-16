

<p>QName Filter: ${filter.name}</p>
<ul>
    <#list filter.QNames as tag>
        <li>
            <#if tag.namespace??>
                ${tag.namespace} : ${tag.localName}
            <#else>
                ${tag.localName}
            </#if>
        </li>
    </#list>
</table>
