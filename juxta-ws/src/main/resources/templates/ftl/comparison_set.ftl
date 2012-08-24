
<div class="info-block">
 
    <table>
        <th colspan="4">Witnesses:</th>
        <#list witnesses as witness>
            <tr>
               <td><a href="${baseUrl}/${workspace}/witness/${witness.id}">${witness.id}</td>
               <td>${witness.name}</a></td>
               <td><a href="${baseUrl}/${workspace}/set/${set.id}/witness/${witness.id}/annotation">Annotations</a></td>
            </tr>
        </#list>
    </table>
    
    <br/>
    
    <p>View: <a href="${baseUrl}/${workspace}/set/${set.id}/alignment?filter=differences">Differences</a></p>
    <p>View: <a href="${baseUrl}/${workspace}/set/${set.id}/view?mode=heatmap">Heatmap</a></p>
    
 
    <#if witnesses?size &gt; 1 >
        <#assign last = witnesses?size>
        <#list 0..last-2 as idx>
            <#list idx..last-1 as idx2 >
                <#if  witnesses[idx].id != witnesses[idx2].id >
                    <#assign id1 = witnesses[idx].id>
                    <#assign id2 = witnesses[idx2].id>
                    <p>
                        View:&nbsp;<a href="${baseUrl}/${workspace}/set/${set.id}/view?mode=sidebyside&docs=${id1},${id2}">Side by Side</a>
                        &nbsp;-&nbsp;${witnesses[idx].name} vs. ${witnesses[idx2].name}
                    </p>
                </#if>   
            </#list>
        </#list>
    </#if>
</div>

