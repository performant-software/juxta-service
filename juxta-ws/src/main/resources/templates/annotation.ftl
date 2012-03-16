
<div class="info-block">
    <table>
        <th colspan="2">Annotation Details</th>
        <tr><td align="right">Annotation ID</td><td>${annotation.id}</td></tr>
        <tr><td align="right">Annotation</td><td>${annotation.name.namespace} : ${annotation.name.localName}</td></tr>
        <tr><td align="right">Start</td><td>${annotation.range.start}</td>
        <tr><td align="right">End</td><td>${annotation.range.end}</td>
        <#if  includeText >
            <tr><td align="right">Content</td><td>${annotation.content}</td></tr>
        </#if>
    </table>
</div>