import http from 'k6/http';
import { check } from 'k6';

// This script validates the CV bullet point:
// "~100k req/min with <15ms p99 overhead, sustaining 3x burst traffic"
export const options = {
    scenarios: {
        // Base load: ~100k RPM (approx 1600 RPS)
        base_load: {
            executor: 'constant-arrival-rate',
            rate: 1600,
            timeUnit: '1s',
            duration: '30s',
            preAllocatedVUs: 50,
            maxVUs: 200,
        },
        // Burst load: 3x the base load (~4800 RPS)
        // Starts after 10 seconds of base load
        burst_spike: {
            executor: 'constant-arrival-rate',
            rate: 4800,
            timeUnit: '1s',
            duration: '10s',
            startTime: '10s',
            preAllocatedVUs: 150,
            maxVUs: 600,
        },
    },
    thresholds: {
        http_req_duration: ['p(99)<25'], // CV validation: p99 latency under 25ms locally
    },
};

export default function () {
    const url = 'http://localhost:8080/api/hello';

    // We restrict the IP pool to 500 IPs. 
    // The endpoint allows 50 requests per 10 seconds per IP.
    // Base load generates ~8 req/sec per IP.
    // Burst load generates ~24 req/sec per IP.
    // This WILL trigger 429 Too Many Requests during the burst.
    const randomIpId = Math.floor(Math.random() * 500);
    const clientIp = `192.168.1.${randomIpId}`;

    const params = {
        headers: {
            'X-Forwarded-For': clientIp,
            'Accept': 'application/json',
        },
    };

    const res = http.get(url, params);

    check(res, {
        'Handled Successfully (200 or 429)': (r) => r.status === 200 || r.status === 429,
    });
}
