import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    vus: 500, // 500 Virtual Users connected at the same time
    duration: '1m',
    thresholds: {
        http_req_duration: ['p(95)<200'], // At least 95% of requests must complete in under 200ms
    },
};

export default function () {
    const url = 'http://localhost:8080/api/hello';

    const clientIp = `192.168.1.${__VU}`;

    const params = {
        headers: {
            'X-Forwarded-For': clientIp,
            'Accept': 'application/json',
        },
    };

    const res = http.get(url, params);

    check(res, {
        'Request Accepted (200)': (r) => r.status === 200,
        'Request Blocked (429)': (r) => r.status === 429,
    });

    sleep(Math.random() * 0.05 + 0.05);
}
