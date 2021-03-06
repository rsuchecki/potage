/* 
 * Copyright 2016 University of Adelaide.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package beans;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.SessionScoped;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.servlet.http.HttpServletRequest;
import org.primefaces.context.RequestContext;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;
import reusable.ExecProcessor;

@ManagedBean(name = "counterBean")
@SessionScoped
//@ViewScoped
//@RequestScoped
public class CounterBean implements Serializable{

    private String recordStats = "";

    public String getRecordStats() {
        recordStats();
        return recordStats;
    }

    private void recordStats() {
        //        String header = "Submit Date/Time | Complete Date/Time | Remote_IP | Forwarded_IP | User_name | Remote_user | e-mail | Input_seqs | ATG_known | retrieved_proms | Browser/System | XML file";
        FacesContext context = FacesContext.getCurrentInstance();
        ExternalContext extContext = context.getExternalContext();
        StringBuilder sb = new StringBuilder();
        String separator = " | ";
        sb.append(new Date()).append(separator);
        //        String ipAddress = extContext..request.getHeader("X-Forwarded-For"); 

        HttpServletRequest request = (HttpServletRequest) extContext.getRequest();
        sb.append(request.getRemoteAddr());
        sb.append(separator);

//        System.err.println("stream: "+stdOut);
//        System.err.println("last: "+toks[toks.length-1]);
        sb.append(extContext.getRequestHeaderMap().get("user-agent"));
        sb.append(separator);
        sb.append(request.getSession().getId());
//        System.err.println(sb.toString());

        try {
            PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter("/var/tomcat/persist/potage_data/visits.txt", true)));
            out.println(sb.toString());
            out.close();
        } catch (IOException e) {
            e.printStackTrace(System.err);
        }
    }

    private static String getStringFromInputStream(InputStream is) {

        BufferedReader br = null;
        StringBuilder sb = new StringBuilder();

        String line;
        try {

            br = new BufferedReader(new InputStreamReader(is));
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return sb.toString();

    }
}
