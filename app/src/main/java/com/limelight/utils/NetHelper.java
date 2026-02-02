package com.limelight.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.annotation.SuppressLint;

import java.net.InetAddress;

public class NetHelper {
    
    /**
     * 检查当前活动网络是否为 VPN
     */
    public static boolean isActiveNetworkVpn(Context context) {
        ConnectivityManager connMgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connMgr == null) {
            return false;
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network activeNetwork = connMgr.getActiveNetwork();
            if (activeNetwork != null) {
                NetworkCapabilities netCaps = connMgr.getNetworkCapabilities(activeNetwork);
                if (netCaps != null) {
                    return netCaps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ||
                            !netCaps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN);
                }
            }
        } else {
            NetworkInfo activeNetworkInfo = connMgr.getActiveNetworkInfo();
            if (activeNetworkInfo != null) {
                return activeNetworkInfo.getType() == ConnectivityManager.TYPE_VPN;
            }
        }

        return false;
    }
    
    /**
     * 检查当前活动网络是否为移动网络
     */
    public static boolean isActiveNetworkMobile(Context context) {
        ConnectivityManager connMgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connMgr == null) {
            return false;
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network activeNetwork = connMgr.getActiveNetwork();
            if (activeNetwork != null) {
                NetworkCapabilities netCaps = connMgr.getNetworkCapabilities(activeNetwork);
                if (netCaps != null) {
                    return netCaps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
                }
            }
        } else {
            NetworkInfo activeNetworkInfo = connMgr.getActiveNetworkInfo();
            if (activeNetworkInfo != null) {
                int type = activeNetworkInfo.getType();
                return type == ConnectivityManager.TYPE_MOBILE ||
                       type == ConnectivityManager.TYPE_MOBILE_DUN ||
                       type == ConnectivityManager.TYPE_MOBILE_HIPRI ||
                       type == ConnectivityManager.TYPE_MOBILE_MMS ||
                       type == ConnectivityManager.TYPE_MOBILE_SUPL ||
                       type == ConnectivityManager.TYPE_WIMAX;
            }
        }

        return false;
    }
    
    /**
     * 检查当前活动网络是否为 WiFi
     */
    public static boolean isActiveNetworkWifi(Context context) {
        ConnectivityManager connMgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connMgr == null) {
            return false;
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network activeNetwork = connMgr.getActiveNetwork();
            if (activeNetwork != null) {
                NetworkCapabilities netCaps = connMgr.getNetworkCapabilities(activeNetwork);
                if (netCaps != null) {
                    return netCaps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
                }
            }
        } else {
            NetworkInfo activeNetworkInfo = connMgr.getActiveNetworkInfo();
            if (activeNetworkInfo != null) {
                return activeNetworkInfo.getType() == ConnectivityManager.TYPE_WIFI;
            }
        }

        return false;
    }
    
    /**
     * 获取网络下行带宽 (Kbps)
     */
    public static int getDownstreamBandwidthKbps(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ConnectivityManager connMgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connMgr != null) {
                Network activeNetwork = connMgr.getActiveNetwork();
                if (activeNetwork != null) {
                    NetworkCapabilities netCaps = connMgr.getNetworkCapabilities(activeNetwork);
                    if (netCaps != null) {
                        return netCaps.getLinkDownstreamBandwidthKbps();
                    }
                }
            }
        }
        return -1; // 未知
    }
    
    /**
     * 判断地址是否为 LAN 地址（私有地址）
     */
    public static boolean isLanAddress(String addressStr) {
        if (addressStr == null || addressStr.isEmpty()) {
            return false;
        }
        
        try {
            InetAddress addr = InetAddress.getByName(addressStr);
            return addr.isSiteLocalAddress() || addr.isLoopbackAddress() || isPrivateAddress(addr);
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 判断地址是否为私有地址 (RFC 1918)
     */
    public static boolean isPrivateAddress(InetAddress addr) {
        byte[] bytes = addr.getAddress();
        if (bytes.length == 4) {
            // IPv4 私有地址范围
            // 10.0.0.0 - 10.255.255.255
            if (bytes[0] == 10) return true;
            // 172.16.0.0 - 172.31.255.255
            if (bytes[0] == (byte) 172 && bytes[1] >= 16 && bytes[1] <= 31) return true;
            // 192.168.0.0 - 192.168.255.255
            if (bytes[0] == (byte) 192 && bytes[1] == (byte) 168) return true;
        }
        return false;
    }

    /**
     * 计算并格式化网络带宽
     * @param currentRxBytes 当前接收字节数
     * @param previousRxBytes 上次接收字节数
     * @param timeInterval 时间间隔（毫秒）
     * @return 格式化后的带宽字符串
     */
    @SuppressLint("DefaultLocale")
    public static String calculateBandwidth(long currentRxBytes, long previousRxBytes, long timeInterval) {
        // 检查时间间隔是否有效
        if (timeInterval <= 0 || timeInterval > 5000) { // 超过5秒的间隔认为无效
            return "N/A";
        }
        
        // 检查字节数是否有效（防止TrafficStats返回异常值）
        if (currentRxBytes < 0 || previousRxBytes < 0) {
            return "N/A";
        }
        
        // 防止字节数回绕（32位系统可能发生）
        long rxBytesDifference = currentRxBytes - previousRxBytes;
        if (rxBytesDifference < 0) {
            // 如果出现负数，可能是计数器重置，返回0
            return "N/A";
        }
        
        // 转换为KB
        long rxBytesPerDifference = rxBytesDifference / 1024;
        double speedKBps = rxBytesPerDifference / ((double) timeInterval / 1000);
        
        if (speedKBps < 1024) {
            return String.format("%.0f K/s", speedKBps);
        }
        return String.format("%.2f M/s", speedKBps / 1024);
    }
}
