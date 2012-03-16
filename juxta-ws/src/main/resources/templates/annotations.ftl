
<div class="info-block">
    <table>
        <tr>
            <th>ID</id>
            <#if includeText> 
                <th>Content</th>
            </#if>
            <th>Annotation</th>
            <th>Start</th>
            <th>End</th> 
        </tr>
        <#list annotations as annotation>
            <tr>
                <td><a href="${baseUrl}/${workspace}/set/${setId}/witness/${witness.id}/annotation/${annotation.id}?content=1"">${annotation.id}</a></td>
                <#if includeText>
                    <td>${annotation.content}</td>
                </#if>

                 <td>${annotation.name.namespace} : ${annotation.name.localName}</td>
    
                <td align="center">${annotation.range.start}</td>
                <td align="center">${annotation.range.end}</td>
            </tr>
        </#list>
    </table>
</div>
