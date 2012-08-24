
<div class="info-block">
    <p>Differences for Comparison Set: <a href="${baseUrl}/${workspace}/set/${setId}">${setName}</a></p>
</div>
    
<#list alignments as align>
    <div class="align-block">
        <p><b>ID:</b> <a href="${baseUrl}/${workspace}/set/${setId}/alignment/${align.id}">${align.id}</a>
           &nbsp;&nbsp;<b>Type:</b> ${align.name.namespace} : ${align.name.localName}
           &nbsp;&nbsp;<b>Edit Distance:</b> ${align.editDistance}</p>
        <table>
            <th>Witness ID</th><th>Annotation ID</th><th>Range</th>
            <#list align.annotations as annotation>
                <tr>
                    <td><a href="${baseUrl}/${workspace}/witness/${annotation.witnessId}">${annotation.witnessId}</a></td>
                    <td><a href="${baseUrl}/${workspace}/set/${setId}/witness/${annotation.witnessId}/annotation/${annotation.id}?content=1">${annotation.id}</a></td>
                    <td>(${annotation.range.start} - ${annotation.range.end})</td>
                </tr>
            </#list>
        </table>
    </div>
</#list>    