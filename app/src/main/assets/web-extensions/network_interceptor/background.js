
browser.webRequest.onCompleted.addListener(
  async function(details) {
    try {
    const data = {
        date: "2025-04-03",
        pc: 1,

    };

      const response = await fetch(details.url,{method:"POST",body: JSON.stringify(data)});
      const text = await response.text();
      console.log("XXXXXXXXXXXX"+JSON.stringify(details))
       console.log("XXXXXXXXXXXX"+text)

      // 发送到原生应用
      browser.runtime.sendNativeMessage("response_logger", {
        url: details.url,
        status: details.statusCode,
        headers: details.responseHeaders,
        body: text
      });
    } catch (error) {
      console.error("Error handling response:", error);
    }
  },
  { urls: ["*://*.jiuyangongshe.com/jystock-app/api/v1/action/field*"] },

// ["blocking", "responseBody"]
);