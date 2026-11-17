// API配置 - 统一管理 axios 实例
// 开发环境使用代理，生产环境使用相对路径
const API_BASE = import.meta.env.VITE_API_BASE || "";
// JWT 解析函数（用于前端获取用户信息）
export function parseJwt(token) {
  try {
    const base64Url = token.split('.')[1];
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
    const jsonPayload = decodeURIComponent(
      atob(base64)
        .split('')
        .map(function(c) {
          return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
        })
        .join('')
    );
    return JSON.parse(jsonPayload);
  } catch (e) {
    console.error('JWT 解析失败:', e);
    return null;
  }
}

// 从 LocalStorage 获取 JWT
export function getJwtFromLocalStorage() {
  return localStorage.getItem('jwt_token');
}

// 从 JWT 获取当前用户信息
export function getCurrentUserFromJwt() {
  const token = getJwtFromLocalStorage();
  if (!token) return null;
  return parseJwt(token);
}

// 创建并配置 axios 实例
let axiosInstance = null;

export async function getAxiosInstance() {
  if (!axiosInstance) {
    const { default: axios } = await import('axios');
    axiosInstance = axios.create({
      baseURL: API_BASE
    });

    // 添加请求拦截器，自动携带 Authorization 头
    axiosInstance.interceptors.request.use(
      config => {
        const token = localStorage.getItem('jwt_token');
        if (token) {
          config.headers['Authorization'] = `Bearer ${token}`;
        }
        return config;
      },
      error => {
        return Promise.reject(error);
      }
    );

    // 添加响应拦截器，处理401错误
    axiosInstance.interceptors.response.use(
      response => response,
      error => {
        if (error.response && error.response.status === 401) {
          console.warn('未授权，清除本地存储并跳转登录');
          localStorage.removeItem('jwt_token');
          clearUserInfoFromLocalStorage();
          window.location.href = `${API_BASE}/`;
        }
        return Promise.reject(error);
      }
    );
  }
  return axiosInstance;
}

// 保存用户信息到 localStorage（用于展示）
export function saveUserInfoToLocalStorage(responseData) {
  if (responseData.username) {
    localStorage.setItem('loginUsername', responseData.username);
  }
  if (responseData.user_id) {
    localStorage.setItem('loginUserId', responseData.user_id);
  }
  if (responseData.roleKeys) {
    localStorage.setItem('loginRoleKeys', JSON.stringify(responseData.roleKeys));
  }
  if (responseData.menus) {
    localStorage.setItem('loginMenus', JSON.stringify(responseData.menus));
  }
}

// 从 localStorage 获取用户信息（用于展示）
export function getUserInfoFromLocalStorage() {
  return {
    username: localStorage.getItem('loginUsername'),
    user_id: localStorage.getItem('loginUserId'),
    roleKeys: JSON.parse(localStorage.getItem('loginRoleKeys') || '[]'),
    menus: JSON.parse(localStorage.getItem('loginMenus') || '[]')
  };
}

// 清除本地存储的用户信息
export function clearUserInfoFromLocalStorage() {
  localStorage.removeItem('loginUsername');
  localStorage.removeItem('loginUserId');
  localStorage.removeItem('loginRoleKeys');
  localStorage.removeItem('loginMenus');
  localStorage.removeItem('loginRole');
}

export const authApi = {
  login: async (data) => {
    const axios = await getAxiosInstance();
    return axios.post('/api/login', data);
  },
  register: async (data) => {
    const axios = await getAxiosInstance();
    return axios.post('/api/register', data);
  },
  updatePassword: async (data) => {
    const axios = await getAxiosInstance();
    return axios.post('/api/update-password', data);
  },
};

export const translateApi = {
  translate: async (data) => {
    const axios = await getAxiosInstance();
    return axios.post('/api/translate', data);
  },
  getHistory: async (params) => {
    const axios = await getAxiosInstance();
    return axios.get('/api/translate/history', { params });
  },
};

export const recommendApi = {
  getRecommend: async (data) => {
    const axios = await getAxiosInstance();
    return axios.post('/api/recommend', data);
  },
  reportClick: async (data) => {
    const axios = await getAxiosInstance();
    return axios.post('/api/stat/click', data);
  },
};

export const userApi = {
  getList: async (params) => {
    const axios = await getAxiosInstance();
    return axios.get('/api/user/list', { params });
  },
  getRoles: async () => {
    const axios = await getAxiosInstance();
    return axios.get('/api/role/list');
  },
  add: async (data) => {
    const axios = await getAxiosInstance();
    return axios.post('/api/user/add', data);
  },
  update: async (data) => {
    const axios = await getAxiosInstance();
    return axios.post('/api/user/update', data);
  },
  delete: async (data) => {
    const axios = await getAxiosInstance();
    return axios.post('/api/user/delete', data);
  },
};

export const merchantApi = {
  getApplications: async (params) => {
    const axios = await getAxiosInstance();
    return axios.get('/api/merchant/applications', { params });
  },
  approve: async (data) => {
    const axios = await getAxiosInstance();
    return axios.post('/api/merchant/approve', data);
  },
  reject: async (data) => {
    const axios = await getAxiosInstance();
    return axios.post('/api/merchant/reject', data);
  },
  getDetail: async (params) => {
    const axios = await getAxiosInstance();
    return axios.get('/api/merchant/detail', { params });
  },
};

export const logApi = {
  getList: async (params) => {
    const axios = await getAxiosInstance();
    return axios.get('/api/log/list', { params });
  },
};

export const dashboardApi = {
  getData: async () => {
    const axios = await getAxiosInstance();
    return axios.get('/api/dashboard');
  },
};

export const statApi = {
  getDaily: async (params) => {
    const axios = await getAxiosInstance();
    return axios.get('/api/stat/daily', { params });
  },
  getClickRank: async () => {
    const axios = await getAxiosInstance();
    return axios.get('/api/stat/click-rank');
  },
  getTranslate: async (params) => {
    const axios = await getAxiosInstance();
    return axios.get('/api/stat/translate', { params });
  },
  generateTranslateReport: async (data) => {
    const axios = await getAxiosInstance();
    return axios.post('/api/stat/translate/generate-report', data);
  },
};

export const profileApi = {
  getUserInfo: async () => {
    const axios = await getAxiosInstance();
    return axios.get('/api/user/profile');
  },
  updateUserInfo: async (data) => {
    const axios = await getAxiosInstance();
    return axios.post('/api/user/profile', data);
  },
  getMerchantInfo: async () => {
    const axios = await getAxiosInstance();
    return axios.get('/api/merchant/info');
  },
  updateMerchantInfo: async (data) => {
    const axios = await getAxiosInstance();
    return axios.post('/api/merchant/info', data);
  },
};

export const productApi = {
  getList: async (params) => {
    const axios = await getAxiosInstance();
    return axios.get('/api/products', { params });
  },
  getByEsin: async (esin, country, language) => {
    const axios = await getAxiosInstance();
    const params = {};
    if (country) params.country = country;
    if (language) params.language = language;
    return axios.get(`/api/product/${esin}`, { params });
  },
  publish: async (data) => {
    const axios = await getAxiosInstance();
    return axios.post('/api/product/publish', data);
  },
  unpublish: async (esin) => {
    const axios = await getAxiosInstance();
    return axios.post(`/api/product/${esin}/unpublish`);
  },
  getMerchantProducts: async (params) => {
    const axios = await getAxiosInstance();
    return axios.get('/api/merchant/products', { params });
  },
  update: async (esin, data) => {
    const axios = await getAxiosInstance();
    return axios.put(`/api/merchant/products/${esin}`, data);
  },
  // 商家下架自己的商品：DELETE /api/merchant/products/{esin}
  // 注意不能用 post(`/api/product/${esin}/unpublish`)——那是管理员接口（网关按 endsWith('/unpublish') 判 admin），商家调用必 403
  deleteMerchantProduct: async (esin) => {
    const axios = await getAxiosInstance();
    return axios.delete(`/api/merchant/products/${esin}`);
  },
  uploadImage: async (file) => {
    const axios = await getAxiosInstance();
    const formData = new FormData();
    formData.append('image', file);
    return axios.post('/api/product/upload-image', formData, {
      headers: {
        'Content-Type': undefined,
      },
    });
  },
};

export const glossaryApi = {
  getList: async (params) => {
    const axios = await getAxiosInstance();
    return axios.get('/api/glossary/list', { params });
  },
  add: async (data) => {
    const axios = await getAxiosInstance();
    return axios.post('/api/glossary/add', data);
  },
  update: async (data) => {
    const axios = await getAxiosInstance();
    return axios.post('/api/glossary/update', data);
  },
  delete: async (data) => {
    const axios = await getAxiosInstance();
    return axios.post('/api/glossary/delete', data);
  },
  match: async (data) => {
    const axios = await getAxiosInstance();
    return axios.post('/api/glossary/match', data);
  },
  apply: async (data) => {
    const axios = await getAxiosInstance();
    return axios.post('/api/glossary/apply', data);
  },
};
