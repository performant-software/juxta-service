
<div class="info-block">
    <p><b>ID:</b>  ${align.id}</p>
    <p><b>Type:</b> ${align.name.namespace} : ${align.name.localName}</p>
    <p><b>Edit Distance:</b> ${align.editDistance}</p>
    <p><b>Comparison Set:</b>  <a href="${baseUrl}/${workspace}/set/${setId}">${setName}</a></p></p>
    <br/>
    <table>
        <th>Witness ID</th><th>Annotaion ID</th><th>Range</th><th>Fragment</th>
        <#list align.annotations as annotation>
            <tr>
                <td><a href="${baseUrl}/${workspace}/witness/${annotation.witnessId}">${annotation.witnessId}</a></td>
                <td><a href="${baseUrl}/${workspace}/set/${setId}/witness/${annotation.witnessId}/annotation/${annotation.id}?content=1">${annotation.id}</a></td>
                <td>(${annotation.range.start} - ${annotation.range.end})</td>
                <td>${annotation.fragment}</td>
            </tr>
        </#list>
    </table>
    <br/>
    <p><a href="${baseUrl}/${workspace}/set/${setId}/alignment?filter=differences">Back</a> to Differences</p>
</div>