

<table>
    <tr>
        <th>ID</th><th>Name</th><th>Source ID</th><th>Template ID</th><th>Revision Set ID</th>
    </tr>
    <#list items as doc>
        <tr>
            <td align="center"><a href="${baseUrl}/${workspace}/witness/${doc.id}">${doc.id}</a></td>
            <td><a href="${baseUrl}/${workspace}/witness/${doc.id}">${doc.name}</a></td>
            <td align="center"><a href="${baseUrl}/${workspace}/source/${doc.sourceId}">${doc.sourceId}</a></td>
            <#if doc.templateId?? >
                <td align="center"><a href="${baseUrl}/${workspace}/template/${doc.templateId}">${doc.templateId}</a></td> 
            <#else>
                <td align="center">N/A</td>    
            </#if>
            <#if doc.revisionSetId?? >
                <td align="center"><a href="${baseUrl}/${workspace}/source/${doc.sourceId}/revision">${doc.revisionSetId}</a></td>
            <#else>
                <td align="center">N/A</td>
            </#if>
        </tr>
    </#list>
</table>