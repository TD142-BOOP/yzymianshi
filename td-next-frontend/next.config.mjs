/** @type {import("next").NextConfig} */
const nextConfig = {
    output: "standalone",
    typescript: {
        // !! WARN !!
        // Dangerously allow production builds to successfully complete even if
        // your project has type errors.
        // !! WARN !!
        ignoreBuildErrors: true
    },
    reactStrictMode: false, // 禁用 React 严格模式
    async rewrites() {
        return [
            {
                source: '/api/:path*',
                destination: 'http://localhost:8101/api/:path*' // 替换为你的后端服务地址
            }
        ];
    }
};

export default nextConfig;
