<html>
    <head>
        <meta charset="utf-8"> 
        <title> 
            ${title}
        </title> 
        <link href="${baseUrl}/stylesheets/juxta-ws.css" rel="stylesheet" type="text/css">
    </head>
    
    <body style="background-color:white">
        
        <div class="info-block">
            <h3>Juxta Metrics Report</h3>
            <table>
                <th>Workspace</th><th>Source Count</th><th>Min Source Size (bytes)</th><th>Max Source Size (bytes)</th>
                <th>Mean Source Size (bytes)</th><th>Total Source Size (bytes)</th>
                <th>Min Set Witnesses</th><th>Max Set Witnesses</th><th>Mean Set Witnesses</th>
                <th>Collating Time (ms)</th><th>Started Collations</th><th>Finished Collations</th>
                <#list metrics as metric>
                    <tr>
                        <td>${metric.workspace}</td>
                        <td>${metric.numSources}</td>
                        <td>${metric.minSourceSize}</td>
                        <td>${metric.maxSourceSize}</td>
                        <td>${metric.meanSourceSize}</td>
                        <td>${metric.totalSourcesSize}</td>
                        <td>${metric.minSetWitnesses}</td>
                        <td>${metric.maxSetWitnesses}</td>
                        <td>${metric.meanSetWitnesses}</td>
                        <td>${metric.totalTimeCollating}</td>
                        <td>${metric.numCollationsStarted}</td>
                        <td>${metric.numCollationsFinished}</td>
                    </tr>
                </#list>
            </table>
        </div>
    </body>
</html>